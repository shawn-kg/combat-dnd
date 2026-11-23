package app.combatdnd.encounter.api

import app.combatdnd.encounter.app.EncounterStatus
import app.combatdnd.encounter.app.Header
import app.combatdnd.encounter.domain.AttackId
import app.combatdnd.encounter.domain.AttackView
import app.combatdnd.encounter.domain.Combatant
import app.combatdnd.encounter.domain.CombatantId
import app.combatdnd.encounter.domain.CombatantView
import app.combatdnd.encounter.domain.Command
import app.combatdnd.encounter.domain.Controller
import app.combatdnd.encounter.domain.EncounterView
import app.combatdnd.encounter.domain.LastTurnView
import app.combatdnd.encounter.domain.PlayerId
import app.combatdnd.encounter.domain.Side
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

// ---------- what the client receives (mirrors frontend/src/api/types.ts) ----------

data class EncounterViewDto(
    val campaignId: String,
    val name: String,
    /** PREPARING, ROLLING_INITIATIVE, ACTIVE or ENDED. */
    val status: EncounterStatus,
    val viewerIsDm: Boolean,
    val round: Int,
    val currentId: String?,
    val version: Long,
    val combatants: List<CombatantViewDto>,
    val attacks: List<AttackViewDto>,
)

data class CombatantViewDto(
    val id: String,
    val name: String,
    val side: Side,
    val portraitKey: String?,
    val initiative: Int,
    val isCurrent: Boolean,
    val controlledByViewer: Boolean,
    val ac: Int?,
    val hp: Int?,
    val maxHp: Int?,
    val hidden: Boolean,
    val defeated: Boolean,
    val lastTurn: LastTurnDto?,
)

data class LastTurnDto(val round: Int, val hits: Int, val misses: Int, val damage: Int, val attacks: List<AttackViewDto>)

data class AttackViewDto(
    val id: String,
    val round: Int,
    val attackerId: String,
    val targetId: String,
    val targetName: String?,
    val hit: Boolean,
    val damage: Int,
)

fun EncounterView.toDto(h: Header) =
    EncounterViewDto(h.campaignId.toString(), h.name, h.status, viewerIsDm, round, currentId?.value, version, combatants.map { it.toDto() }, attacks.map { it.toDto() })

private fun CombatantView.toDto() = CombatantViewDto(
    id.value, name, side, portraitKey, initiative, isCurrent, controlledByViewer, ac, hp, maxHp, hidden, defeated, lastTurn?.toDto(),
)

private fun LastTurnView.toDto() = LastTurnDto(round, hits, misses, damage, attacks.map { it.toDto() })

private fun AttackView.toDto() = AttackViewDto(id.value, round, attackerId.value, targetId.value, targetName, hit, damage)

// ---------- what the client sends: {"type": "LogAttack", ..., "expectedVersion": 12} ----------

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(CommandRequest.LogAttack::class, name = "LogAttack"),
    JsonSubTypes.Type(CommandRequest.EditAttack::class, name = "EditAttack"),
    JsonSubTypes.Type(CommandRequest.DeleteAttack::class, name = "DeleteAttack"),
    JsonSubTypes.Type(CommandRequest.NextTurn::class, name = "NextTurn"),
    JsonSubTypes.Type(CommandRequest.PreviousTurn::class, name = "PreviousTurn"),
    JsonSubTypes.Type(CommandRequest.AddCombatant::class, name = "AddCombatant"),
    JsonSubTypes.Type(CommandRequest.AddCombatants::class, name = "AddCombatants"),
    JsonSubTypes.Type(CommandRequest.RemoveCombatant::class, name = "RemoveCombatant"),
    JsonSubTypes.Type(CommandRequest.RevealCombatant::class, name = "RevealCombatant"),
    JsonSubTypes.Type(CommandRequest.SetDefeated::class, name = "SetDefeated"),
    JsonSubTypes.Type(CommandRequest.EditCombatant::class, name = "EditCombatant"),
    JsonSubTypes.Type(CommandRequest.Reorder::class, name = "Reorder"),
    JsonSubTypes.Type(CommandRequest.SetInitiatives::class, name = "SetInitiatives"),
)
sealed interface CommandRequest {
    val expectedVersion: Long?
    fun toCommand(): Command

    data class LogAttack(
        val attackerId: String,
        val targetId: String,
        val hit: Boolean,
        val damage: Int = 0,
        override val expectedVersion: Long? = null,
    ) : CommandRequest {
        override fun toCommand() = Command.LogAttack(CombatantId(attackerId), CombatantId(targetId), hit, damage)
    }

    data class EditAttack(val attackId: String, val hit: Boolean, val damage: Int = 0, override val expectedVersion: Long? = null) :
        CommandRequest {
        override fun toCommand() = Command.EditAttack(AttackId(attackId), hit, damage)
    }

    data class DeleteAttack(val attackId: String, override val expectedVersion: Long? = null) : CommandRequest {
        override fun toCommand() = Command.DeleteAttack(AttackId(attackId))
    }

    data class NextTurn(override val expectedVersion: Long? = null) : CommandRequest {
        override fun toCommand() = Command.NextTurn
    }

    data class PreviousTurn(override val expectedVersion: Long? = null) : CommandRequest {
        override fun toCommand() = Command.PreviousTurn
    }

    data class AddCombatant(
        val id: String,
        val name: String,
        val side: Side,
        /** The player who controls it; null for enemies and DM-run allies. */
        val playerId: String? = null,
        val initiative: Int,
        val ac: Int,
        val hp: Int,
        val maxHp: Int,
        val portraitKey: String? = null,
        val hidden: Boolean = false,
        override val expectedVersion: Long? = null,
    ) : CommandRequest {
        override fun toCommand() =
            Command.AddCombatant(NewCombatant(id, name, side, playerId, initiative, ac, hp, maxHp, portraitKey, hidden).toCombatant())
    }

    /** Several at once, as one command, so one Undo removes them all. */
    data class AddCombatants(val combatants: List<NewCombatant>, override val expectedVersion: Long? = null) : CommandRequest {
        override fun toCommand() = Command.AddCombatants(combatants.map { it.toCombatant() })
    }

    data class RemoveCombatant(val id: String, override val expectedVersion: Long? = null) : CommandRequest {
        override fun toCommand() = Command.RemoveCombatant(CombatantId(id))
    }

    data class RevealCombatant(val id: String, override val expectedVersion: Long? = null) : CommandRequest {
        override fun toCommand() = Command.RevealCombatant(CombatantId(id))
    }

    data class SetDefeated(val id: String, val defeated: Boolean, override val expectedVersion: Long? = null) : CommandRequest {
        override fun toCommand() = Command.SetDefeated(CombatantId(id), defeated)
    }

    data class EditCombatant(
        val id: String,
        val name: String? = null,
        val portraitKey: String? = null,
        val initiative: Int? = null,
        val ac: Int? = null,
        val hp: Int? = null,
        val maxHp: Int? = null,
        override val expectedVersion: Long? = null,
    ) : CommandRequest {
        override fun toCommand() = Command.EditCombatant(CombatantId(id), name, portraitKey, initiative, ac, hp, maxHp)
    }

    data class Reorder(val order: List<String>, override val expectedVersion: Long? = null) : CommandRequest {
        override fun toCommand() = Command.Reorder(order.map(::CombatantId))
    }

    /** {"type":"SetInitiatives","initiatives":{"<combatant id>": 17, ...}} */
    data class SetInitiatives(val initiatives: Map<String, Int>, override val expectedVersion: Long? = null) : CommandRequest {
        override fun toCommand() = Command.SetInitiatives(initiatives.mapKeys { CombatantId(it.key) })
    }
}

/** One combatant to add. A plain shape (no "type"), so it can sit inside AddCombatants' list. */
data class NewCombatant(
    val id: String,
    val name: String,
    val side: Side,
    /** The player who controls it; null for enemies and DM-run allies. */
    val playerId: String? = null,
    val initiative: Int,
    val ac: Int,
    val hp: Int,
    val maxHp: Int,
    val portraitKey: String? = null,
    val hidden: Boolean = false,
) {
    fun toCombatant() = Combatant(
        id = CombatantId(id),
        name = name,
        side = side,
        controller = playerId?.let { Controller.Player(PlayerId(it)) } ?: Controller.Dm,
        initiative = initiative,
        ac = ac,
        hp = hp,
        maxHp = maxHp,
        portraitKey = portraitKey,
        hidden = hidden,
    )
}

data class VersionResponse(val version: Long)

data class ErrorResponse(val message: String)
