package app.combatdnd.encounter.prep

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

data class EnemyRow(val templateId: UUID, val count: Int, val hidden: Boolean)

/** The saved roster: encounter_party (who's in) and encounter_enemy (which monsters, how many). */
@Repository
class PrepRepository(private val jdbc: JdbcClient) {

    /** character id -> included. Characters without a row are included. */
    fun party(encounterId: UUID): Map<UUID, Boolean> =
        jdbc.sql("select character_id, included from encounter_party where encounter_id = ?").param(encounterId)
            .query { rs, _ -> rs.getObject("character_id", UUID::class.java) to rs.getBoolean("included") }
            .list().toMap()

    fun enemies(encounterId: UUID): List<EnemyRow> =
        jdbc.sql("select monster_template_id, count, hidden from encounter_enemy where encounter_id = ? order by sort_order")
            .param(encounterId)
            .query { rs, _ -> EnemyRow(rs.getObject("monster_template_id", UUID::class.java), rs.getInt("count"), rs.getBoolean("hidden")) }
            .list()

    fun rename(encounterId: UUID, name: String) {
        jdbc.sql("update encounter set name = ? where id = ?").params(name, encounterId).update()
    }

    fun replace(encounterId: UUID, party: List<PartyChoice>, enemies: List<EnemyChoice>) {
        jdbc.sql("delete from encounter_party where encounter_id = ?").param(encounterId).update()
        jdbc.sql("delete from encounter_enemy where encounter_id = ?").param(encounterId).update()
        party.forEach {
            jdbc.sql("insert into encounter_party (encounter_id, character_id, included) values (?, ?, ?)")
                .params(encounterId, it.characterId, it.included).update()
        }
        enemies.forEachIndexed { i, e ->
            jdbc.sql("insert into encounter_enemy (encounter_id, monster_template_id, count, hidden, sort_order) values (?, ?, ?, ?, ?)")
                .params(encounterId, e.templateId, e.count, e.hidden, i).update()
        }
    }

    /** End of a fight: HP carries over to the next one. Capped at the character's max in case it changed. */
    fun saveHp(campaignId: UUID, characterId: UUID, hp: Int) {
        jdbc.sql("update character set current_hp = least(?, max_hp) where id = ? and campaign_id = ?")
            .params(hp, characterId, campaignId).update()
    }
}
