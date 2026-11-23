package app.combatdnd.encounter.prep

import app.combatdnd.encounter.app.EncounterStatus
import java.util.UUID

data class CreateEncounterRequest(val name: String)

data class EncounterCreated(val id: UUID)

/** The saved setup of one encounter, for the DM's prep page. */
data class PrepView(
    val id: UUID,
    val campaignId: UUID,
    val name: String,
    val status: EncounterStatus,
    /** Every character in the campaign; untick the ones who aren't playing. */
    val party: List<PrepPartyMember>,
    val enemies: List<PrepEnemy>,
)

data class PrepPartyMember(
    val characterId: UUID,
    val name: String,
    val portraitKey: String?,
    val ac: Int,
    val hp: Int,
    val maxHp: Int,
    val playerName: String?,
    val included: Boolean,
)

data class PrepEnemy(
    val templateId: UUID,
    val name: String,
    val portraitKey: String?,
    val ac: Int,
    val maxHp: Int,
    val count: Int,
    val hidden: Boolean,
)

/** Saves the whole setup at once. Characters left out of `party` count as included. */
data class PrepRequest(val name: String, val party: List<PartyChoice> = emptyList(), val enemies: List<EnemyChoice> = emptyList())

data class PartyChoice(val characterId: UUID, val included: Boolean)

data class EnemyChoice(val templateId: UUID, val count: Int = 1, val hidden: Boolean = false)
