package app.combatdnd.encounter.domain

import app.combatdnd.encounter.domain.AttackStage.AWAITING_DAMAGE
import app.combatdnd.encounter.domain.AttackStage.DAMAGE_SUBMITTED
import app.combatdnd.encounter.domain.AttackStage.DECLARED
import app.combatdnd.encounter.domain.AttackStage.REACTION_WINDOW
import app.combatdnd.encounter.domain.AttackStage.RESUBMIT

/** Apply one event. Pure: the current state is `events.fold(initial, ::evolve)`. */
fun evolve(state: EncounterState, event: Event): EncounterState {
    val next = when (event) {
        is Event.AttackDeclared -> state
            .update(event.attackerId) { it.spend(event.cost, isAttack = event.kind == AttackKind.ATTACK) }
            .copy(
                pending = PendingAttack(
                    attackerId = event.attackerId,
                    targetId = event.targetId,
                    label = event.label,
                    kind = event.kind,
                    roll = event.roll,
                    rollMode = event.rollMode,
                    natural = event.natural,
                    early = event.early,
                    stage = DECLARED,
                ),
            )

        is Event.AttackResolved -> {
            val p = state.pending!!
            if (!event.hit) state.copy(pending = null)
            else state.copy(pending = p.copy(stage = stageAfterHit(state.combatant(p.targetId))))
        }

        is Event.AttackSentBack -> {
            val p = state.pending!!
            val stage = if (p.stage == DAMAGE_SUBMITTED) AWAITING_DAMAGE else RESUBMIT
            state.copy(pending = p.copy(stage = stage, sendBackReason = event.reason))
        }

        is Event.AttackResubmitted -> state.copy(
            pending = state.pending!!.copy(
                stage = DECLARED,
                roll = event.roll,
                rollMode = event.rollMode,
                natural = event.natural,
                sendBackReason = null,
            ),
        )

        is Event.ReactionUsed -> {
            val after = state.update(event.combatantId) {
                it.copy(
                    acBonus = it.acBonus + event.acBonus,
                    resources = it.resources.copy(reactionUsed = true),
                )
            }
            val p = after.pending
            val reactsToHit = p != null && p.targetId == event.combatantId &&
                (p.stage == REACTION_WINDOW || (event.automatic && p.stage == AWAITING_DAMAGE))
            when {
                !reactsToHit -> after
                turnsIntoMiss(p, after.combatant(event.combatantId)) -> after.copy(pending = null)
                else -> after.copy(pending = p.copy(stage = AWAITING_DAMAGE))
            }
        }

        Event.ReactionSkipped -> state.copy(pending = state.pending!!.copy(stage = AWAITING_DAMAGE))

        is Event.DamageSubmitted -> state.copy(
            pending = state.pending!!.copy(stage = DAMAGE_SUBMITTED, damage = event.amount, damageType = event.damageType),
        )

        is Event.DamageApplied -> state
            .update(event.targetId) { it.takeDamage(event.amount) }
            .copy(pending = null)

        is Event.StandardActionUsed -> state.update(event.combatantId) {
            val spent = it.spend(event.cost, isAttack = false)
            if (event.label == "Dash") spent.copy(resources = spent.resources.copy(dashed = true)) else spent
        }

        is Event.TurnAdvanced -> state
            .update(event.nextId) { it.copy(resources = Resources(), acBonus = 0) }
            .copy(currentId = event.nextId, round = event.round, pending = null)
    }
    return next.copy(version = state.version + 1)
}

/** The reaction window opens only for a party target with a reaction left that wants to be asked. */
private fun stageAfterHit(target: Combatant): AttackStage {
    if (target.side != Side.PARTY || target.resources.reactionUsed) return AWAITING_DAMAGE
    val wantsToBeAsked = target.reactions.isEmpty() || target.reactions.any { it.mode == ReactionMode.ASK }
    return if (wantsToBeAsked) REACTION_WINDOW else AWAITING_DAMAGE
}

private fun turnsIntoMiss(attack: PendingAttack, target: Combatant): Boolean =
    attack.kind == AttackKind.ATTACK && attack.natural != Natural.TWENTY && attack.roll < target.effectiveAc

private fun Combatant.spend(cost: Cost, isAttack: Boolean): Combatant {
    val r = resources
    val spent = when (cost) {
        Cost.ACTION -> r.copy(actionUsed = true, attacksThisTurn = r.attacksThisTurn + if (isAttack) 1 else 0)
        Cost.BONUS -> r.copy(bonusUsed = true)
        Cost.REACTION -> r.copy(reactionUsed = true)
        Cost.FREE -> r
    }
    return copy(resources = spent)
}

private fun Combatant.takeDamage(amount: Int): Combatant {
    val hpLeft = (hp - amount).coerceAtLeast(0)
    val knockedOut = hpLeft == 0 && conditions.none { it.name == "Unconscious" }
    // An enemy's Unconscious is private: it would reveal its health.
    val newConditions = if (knockedOut) conditions + Condition("Unconscious", private = side == Side.ENEMY) else conditions
    return copy(hp = hpLeft, conditions = newConditions)
}
