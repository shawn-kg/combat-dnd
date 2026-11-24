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
    /** Every NPC in the campaign; `included` ones are in this fight. */
    val npcs: List<PrepNpc> = emptyList(),
)

data class PrepNpc(
    val npcId: UUID,
    val name: String,
    val portraitKey: String?,
    val ac: Int,
    val hp: Int,
    val maxHp: Int,
    val statsVisible: Boolean,
    val included: Boolean,
    val hidden: Boolean,
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
data class PrepRequest(
    val name: String,
    val party: List<PartyChoice> = emptyList(),
    val enemies: List<EnemyChoice> = emptyList(),
    /** The NPCs in this fight (others are left out). */
    val npcs: List<NpcChoice> = emptyList(),
)

data class NpcChoice(val npcId: UUID, val hidden: Boolean = false)

data class PartyChoice(val characterId: UUID, val included: Boolean)

data class EnemyChoice(val templateId: UUID, val count: Int = 1, val hidden: Boolean = false)
