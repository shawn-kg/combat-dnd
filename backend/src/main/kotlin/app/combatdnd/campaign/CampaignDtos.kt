package app.combatdnd.campaign

import app.combatdnd.auth.PlayerStatus
import app.combatdnd.encounter.app.EncounterSummary
import java.util.UUID

// ---------- requests ----------

data class CreateCampaignRequest(val name: String)

/** Either a new player's name, or the id of a player rejoining from a new browser. */
data class JoinRequest(val name: String? = null, val returningPlayerId: UUID? = null)

data class CharacterRequest(
    val name: String,
    val ac: Int,
    val maxHp: Int,
    /** Defaults to max HP for a new character. */
    val hp: Int? = null,
    val portraitKey: String? = null,
)

data class HpRequest(val hp: Int)

data class TemplateRequest(val name: String, val ac: Int, val maxHp: Int, val portraitKey: String? = null)

// ---------- responses ----------

data class CampaignCreated(val id: UUID, val joinCode: String)

data class JoinInfo(val campaignId: UUID, val campaignName: String, val players: List<JoinPlayer>)

data class JoinPlayer(val id: UUID, val name: String)

data class Joined(val campaignId: UUID, val playerId: UUID)

data class JoinCode(val joinCode: String)

data class MyCampaign(val id: UUID, val name: String, val role: String)

data class PlayerDto(val id: UUID, val name: String, val status: PlayerStatus, val characterId: UUID?)

data class CharacterDto(
    val id: UUID,
    val name: String,
    val portraitKey: String?,
    val ac: Int,
    val hp: Int,
    val maxHp: Int,
    val playerId: UUID?,
    val playerName: String?,
)

data class TemplateDto(val id: UUID, val name: String, val portraitKey: String?, val ac: Int, val maxHp: Int)

/** What a browser sees on a campaign's home page. `joinCode` and `templates` are only sent to the DM. */
data class CampaignView(
    val id: UUID,
    val name: String,
    val role: String,
    val joinCode: String?,
    /** The DM sees every player; a player sees only themselves. */
    val players: List<PlayerDto>,
    /** Empty for a player who isn't approved yet. */
    val characters: List<CharacterDto>,
    val me: PlayerDto?,
    val templates: List<TemplateDto> = emptyList(),
    /** The DM sees every encounter, newest first; players only see fights that are under way. */
    val encounters: List<EncounterSummary> = emptyList(),
)
