package app.combatdnd.campaign

import app.combatdnd.auth.PlayerStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

data class CampaignRow(val id: UUID, val name: String, val joinCode: String)

data class PlayerRow(val id: UUID, val campaignId: UUID, val name: String, val status: PlayerStatus)

data class TemplateRow(val id: UUID, val campaignId: UUID, val name: String, val portraitKey: String?, val ac: Int, val maxHp: Int)

data class CharacterRow(
    val id: UUID,
    val campaignId: UUID,
    val playerId: UUID?,
    val name: String,
    val portraitKey: String?,
    val ac: Int,
    val maxHp: Int,
    val hp: Int,
)

@Repository
class CampaignRepository(private val jdbc: JdbcClient) {

    // ---------- campaigns ----------

    fun create(name: String, joinCode: String, dmTokenHash: String): UUID =
        jdbc.sql("insert into campaign (name, join_code, dm_token_hash) values (?, ?, ?) returning id")
            .params(name, joinCode, dmTokenHash).query(UUID::class.java).single()

    fun campaign(id: UUID): CampaignRow? =
        jdbc.sql("select id, name, join_code from campaign where id = ?").param(id).query(::campaignRow).optional().orElse(null)

    fun campaignByCode(code: String): CampaignRow? =
        jdbc.sql("select id, name, join_code from campaign where join_code = ?").param(code).query(::campaignRow).optional().orElse(null)

    fun setJoinCode(id: UUID, code: String) {
        jdbc.sql("update campaign set join_code = ? where id = ?").params(code, id).update()
    }

    fun codeTaken(code: String): Boolean =
        jdbc.sql("select count(*) from campaign where join_code = ?").param(code).query(Long::class.java).single() > 0

    private fun campaignRow(rs: ResultSet, @Suppress("UNUSED_PARAMETER") n: Int) =
        CampaignRow(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getString("join_code"))

    // ---------- players ----------

    fun players(campaignId: UUID): List<PlayerRow> =
        jdbc.sql("select id, campaign_id, display_name, status from player where campaign_id = ? and status <> 'REMOVED' order by created_at")
            .param(campaignId).query(::playerRow).list()

    fun player(campaignId: UUID, playerId: UUID): PlayerRow? =
        jdbc.sql("select id, campaign_id, display_name, status from player where campaign_id = ? and id = ?")
            .params(campaignId, playerId).query(::playerRow).optional().orElse(null)

    fun createPlayer(campaignId: UUID, name: String, tokenHash: String): UUID =
        jdbc.sql("insert into player (campaign_id, display_name, token_hash, status) values (?, ?, ?, 'PENDING') returning id")
            .params(campaignId, name, tokenHash).query(UUID::class.java).single()

    /** A returning player on a new browser: a new secret, and back to PENDING until the DM approves again. */
    fun resetPlayer(playerId: UUID, tokenHash: String) {
        jdbc.sql("update player set token_hash = ?, status = 'PENDING' where id = ?").params(tokenHash, playerId).update()
    }

    fun setStatus(playerId: UUID, status: PlayerStatus) {
        jdbc.sql("update player set status = ? where id = ?").params(status.name, playerId).update()
    }

    /** Removed players lose their sign-in and their character goes back to unclaimed. */
    fun removePlayer(playerId: UUID) {
        jdbc.sql("update character set player_id = null where player_id = ?").param(playerId).update()
        jdbc.sql("update player set status = 'REMOVED', token_hash = null where id = ?").param(playerId).update()
    }

    private fun playerRow(rs: ResultSet, @Suppress("UNUSED_PARAMETER") n: Int) = PlayerRow(
        rs.getObject("id", UUID::class.java), rs.getObject("campaign_id", UUID::class.java),
        rs.getString("display_name"), PlayerStatus.valueOf(rs.getString("status")),
    )

    // ---------- characters ----------

    private val characterCols = "id, campaign_id, player_id, name, portrait_key, ac, max_hp, current_hp"

    fun characters(campaignId: UUID): List<CharacterRow> =
        jdbc.sql("select $characterCols from character where campaign_id = ? order by name").param(campaignId).query(::characterRow).list()

    fun character(campaignId: UUID, id: UUID): CharacterRow? =
        jdbc.sql("select $characterCols from character where campaign_id = ? and id = ?").params(campaignId, id)
            .query(::characterRow).optional().orElse(null)

    fun characterOf(playerId: UUID): CharacterRow? =
        jdbc.sql("select $characterCols from character where player_id = ?").param(playerId).query(::characterRow).optional().orElse(null)

    fun createCharacter(campaignId: UUID, name: String, portraitKey: String?, ac: Int, maxHp: Int, hp: Int): UUID =
        jdbc.sql(
            "insert into character (campaign_id, name, portrait_key, ac, max_hp, current_hp) values (?, ?, ?, ?, ?, ?) returning id",
        ).params(listOf(campaignId, name, portraitKey, ac, maxHp, hp)).query(UUID::class.java).single()

    fun updateCharacter(id: UUID, name: String, portraitKey: String?, ac: Int, maxHp: Int, hp: Int) {
        jdbc.sql("update character set name = ?, portrait_key = ?, ac = ?, max_hp = ?, current_hp = ? where id = ?")
            .params(listOf(name, portraitKey, ac, maxHp, hp, id)).update()
    }

    fun setHp(id: UUID, hp: Int) {
        jdbc.sql("update character set current_hp = ? where id = ?").params(hp, id).update()
    }

    fun deleteCharacter(id: UUID) {
        jdbc.sql("delete from character where id = ?").param(id).update()
    }

    /** Claims only if still unclaimed; false when someone got there first. */
    fun claim(characterId: UUID, playerId: UUID): Boolean =
        jdbc.sql("update character set player_id = ? where id = ? and player_id is null").params(playerId, characterId).update() == 1

    fun unassign(characterId: UUID) {
        jdbc.sql("update character set player_id = null where id = ?").param(characterId).update()
    }

    // ---------- monster templates ----------

    fun templates(campaignId: UUID): List<TemplateRow> =
        jdbc.sql("select id, campaign_id, name, portrait_key, ac, max_hp from monster_template where campaign_id = ? order by name")
            .param(campaignId).query(::templateRow).list()

    fun template(campaignId: UUID, id: UUID): TemplateRow? =
        jdbc.sql("select id, campaign_id, name, portrait_key, ac, max_hp from monster_template where campaign_id = ? and id = ?")
            .params(campaignId, id).query(::templateRow).optional().orElse(null)

    fun createTemplate(campaignId: UUID, name: String, portraitKey: String?, ac: Int, maxHp: Int): UUID =
        jdbc.sql("insert into monster_template (campaign_id, name, portrait_key, ac, max_hp) values (?, ?, ?, ?, ?) returning id")
            .params(listOf(campaignId, name, portraitKey, ac, maxHp)).query(UUID::class.java).single()

    fun updateTemplate(id: UUID, name: String, portraitKey: String?, ac: Int, maxHp: Int) {
        jdbc.sql("update monster_template set name = ?, portrait_key = ?, ac = ?, max_hp = ? where id = ?")
            .params(listOf(name, portraitKey, ac, maxHp, id)).update()
    }

    fun deleteTemplate(id: UUID) {
        jdbc.sql("delete from monster_template where id = ?").param(id).update()
    }

    private fun templateRow(rs: ResultSet, @Suppress("UNUSED_PARAMETER") n: Int) = TemplateRow(
        rs.getObject("id", UUID::class.java), rs.getObject("campaign_id", UUID::class.java), rs.getString("name"),
        rs.getString("portrait_key"), rs.getInt("ac"), rs.getInt("max_hp"),
    )

    private fun characterRow(rs: ResultSet, @Suppress("UNUSED_PARAMETER") n: Int) = CharacterRow(
        id = rs.getObject("id", UUID::class.java),
        campaignId = rs.getObject("campaign_id", UUID::class.java),
        playerId = rs.getObject("player_id", UUID::class.java),
        name = rs.getString("name"),
        portraitKey = rs.getString("portrait_key"),
        ac = rs.getInt("ac"),
        maxHp = rs.getInt("max_hp"),
        hp = rs.getInt("current_hp"),
    )
}
