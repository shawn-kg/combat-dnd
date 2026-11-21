package app.combatdnd.encounter.domain

import app.combatdnd.encounter.domain.AttackStage.AWAITING_DAMAGE
import app.combatdnd.encounter.domain.AttackStage.DAMAGE_SUBMITTED
import app.combatdnd.encounter.domain.AttackStage.DECLARED
import app.combatdnd.encounter.domain.AttackStage.REACTION_WINDOW
import app.combatdnd.encounter.domain.AttackStage.RESUBMIT
import app.combatdnd.encounter.domain.Command.ApplyDamage
import app.combatdnd.encounter.domain.Command.DeclareAttack
import app.combatdnd.encounter.domain.Command.EnemyReact
import app.combatdnd.encounter.domain.Command.NextTurn
import app.combatdnd.encounter.domain.Command.React
import app.combatdnd.encounter.domain.Command.ResolveAttack
import app.combatdnd.encounter.domain.Command.Resubmit
import app.combatdnd.encounter.domain.Command.SendBack
import app.combatdnd.encounter.domain.Command.SkipReaction
import app.combatdnd.encounter.domain.Command.SubmitDamage
import app.combatdnd.encounter.domain.Command.UseReaction
import app.combatdnd.encounter.domain.Command.UseStandardAction

sealed interface Decision {
    data class Accepted(val events: List<Event>) : Decision
    data class Rejected(val reason: Reason, val message: String) : Decision
}

enum class Reason { FORBIDDEN, INVALID }

private fun ok(vararg events: Event) = Decision.Accepted(events.toList())
private fun forbidden(message: String) = Decision.Rejected(Reason.FORBIDDEN, message)
private fun invalid(message: String) = Decision.Rejected(Reason.INVALID, message)

/**
 * May this actor do this now, and what happens if so?
 *
 * Checks only structure: whose turn it is, what is pending, who controls what.
 * Never checks resource limits (track, don't enforce).
 */
fun decide(state: EncounterState, cmd: Command, actor: Actor): Decision {
    val pending = state.pending
    return when (cmd) {
        is DeclareAttack -> {
            val attacker = state.current
            val target = state.combatants[cmd.targetId] ?: return invalid("Unknown target.")
            when {
                !actor.controls(attacker) -> forbidden("It is not your turn.")
                pending != null -> invalid("Finish the pending attack first.")
                target.id == attacker.id -> invalid("Pick someone other than yourself.")
                target.defeated -> invalid("${target.name} is already defeated.")
                target.hidden && actor != Actor.Dm -> invalid("Unknown target.")
                else -> ok(
                    Event.AttackDeclared(
                        attacker.id, target.id, cmd.label, cmd.kind, cmd.cost, cmd.costNote,
                        cmd.roll, cmd.rollMode, cmd.natural, cmd.early,
                    ),
                )
            }
        }

        is ResolveAttack -> when {
            actor != Actor.Dm -> forbidden("Only the DM calls hits and misses.")
            pending?.stage != DECLARED -> invalid("There is no attack to call.")
            !cmd.hit -> ok(Event.AttackResolved(false))
            else -> {
                val auto = automaticReaction(state, pending)
                if (auto == null) ok(Event.AttackResolved(true))
                else ok(Event.AttackResolved(true), Event.ReactionUsed(pending.targetId, auto.name, auto.acBonus, automatic = true))
            }
        }

        is EnemyReact -> {
            val target = pending?.let { state.combatant(it.targetId) }
            val reaction = target?.reactions?.find { it.name == cmd.reaction }
            when {
                actor != Actor.Dm -> forbidden("Only the DM uses enemy reactions.")
                pending?.stage != DECLARED || target == null -> invalid("There is no attack to react to.")
                target.side != Side.ENEMY -> invalid("Only enemies react this way.")
                reaction == null -> invalid("${target.name} has no reaction called ${cmd.reaction}.")
                else -> ok(Event.ReactionUsed(target.id, reaction.name, reaction.acBonus, automatic = false))
            }
        }

        is SendBack -> when {
            actor != Actor.Dm -> forbidden("Only the DM can send a step back.")
            cmd.reason.isBlank() -> invalid("Give a reason.")
            pending?.stage != DECLARED && pending?.stage != DAMAGE_SUBMITTED -> invalid("There is nothing to send back.")
            else -> ok(Event.AttackSentBack(cmd.reason.trim()))
        }

        is Resubmit -> when {
            pending?.stage != RESUBMIT -> invalid("Nothing was sent back.")
            !actor.controls(state.combatant(pending.attackerId)) -> forbidden("Only the attacker can resubmit.")
            else -> ok(Event.AttackResubmitted(cmd.roll, cmd.rollMode, cmd.natural))
        }

        is React -> {
            val target = pending?.let { state.combatant(it.targetId) }
            when {
                pending?.stage != REACTION_WINDOW || target == null -> invalid("There is nothing to react to.")
                !actor.controls(target) -> forbidden("Only ${target.name}'s player can react.")
                else -> {
                    val def = target.reactions.find { it.name == cmd.reaction }
                    ok(Event.ReactionUsed(target.id, cmd.reaction, def?.acBonus ?: 0, automatic = false))
                }
            }
        }

        SkipReaction -> {
            val target = pending?.let { state.combatant(it.targetId) }
            when {
                pending?.stage != REACTION_WINDOW || target == null -> invalid("There is nothing to react to.")
                !actor.controls(target) -> forbidden("Only ${target.name}'s player or the DM can skip.")
                else -> ok(Event.ReactionSkipped)
            }
        }

        is SubmitDamage -> {
            val attacker = pending?.let { state.combatant(it.attackerId) }
            when {
                pending?.stage != AWAITING_DAMAGE || attacker == null -> invalid("No damage is expected right now.")
                !actor.controls(attacker) -> forbidden("Only the attacker enters damage.")
                cmd.amount < 0 -> invalid("Damage can't be negative.")
                // The DM's own damage needs no confirmation step.
                attacker.controller == Controller.Dm -> ok(
                    Event.DamageSubmitted(cmd.amount, cmd.damageType),
                    Event.DamageApplied(pending.targetId, cmd.amount, cmd.damageType),
                )
                else -> ok(Event.DamageSubmitted(cmd.amount, cmd.damageType))
            }
        }

        is ApplyDamage -> when {
            actor != Actor.Dm -> forbidden("Only the DM applies damage.")
            pending?.stage != DAMAGE_SUBMITTED -> invalid("There is no damage to apply.")
            cmd.amount < 0 -> invalid("Damage can't be negative.")
            else -> ok(Event.DamageApplied(pending.targetId, cmd.amount, pending.damageType ?: "damage"))
        }

        is UseReaction -> {
            val c = state.combatants[cmd.combatantId] ?: return invalid("Unknown combatant.")
            if (!actor.controls(c)) forbidden("That isn't your character.")
            else ok(Event.ReactionUsed(c.id, cmd.label, 0, automatic = false))
        }

        is UseStandardAction -> when {
            !actor.controls(state.current) -> forbidden("It is not your turn.")
            pending != null -> invalid("Finish the pending attack first.")
            else -> ok(Event.StandardActionUsed(state.currentId, cmd.label, cmd.cost))
        }

        NextTurn -> when {
            actor != Actor.Dm && !actor.controls(state.current) -> forbidden("It is not your turn.")
            actor != Actor.Dm && pending != null -> invalid("Finish the pending attack first.")
            else -> {
                val (nextId, round) = nextTurn(state)
                ok(Event.TurnAdvanced(nextId, round))
            }
        }
    }
}

/**
 * A reaction set to AUTO_IF_TURNS_MISS fires only when its AC bonus would turn this hit into a miss.
 * Never on a natural 20.
 */
internal fun automaticReaction(state: EncounterState, attack: PendingAttack): ReactionDef? {
    val target = state.combatant(attack.targetId)
    if (attack.kind != AttackKind.ATTACK || attack.natural == Natural.TWENTY) return null
    if (target.side != Side.PARTY || target.resources.reactionUsed) return null
    return target.reactions.firstOrNull {
        it.mode == ReactionMode.AUTO_IF_TURNS_MISS && it.acBonus > 0 && attack.roll < target.effectiveAc + it.acBonus
    }
}

/** Next combatant in initiative order, skipping hidden and downed ones; the round goes up on wrap-around. */
internal fun nextTurn(state: EncounterState): Pair<CombatantId, Int> {
    var index = state.order.indexOf(state.currentId)
    var round = state.round
    repeat(state.order.size) {
        index = (index + 1) % state.order.size
        if (index == 0) round++
        val c = state.combatant(state.order[index])
        if (!c.hidden && !c.isDown) return c.id to round
    }
    return state.currentId to round
}
