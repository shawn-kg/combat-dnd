package app.combatdnd.encounter.domain

/** Something that happened. Stored in encounter_event and folded with `evolve`. */
sealed interface Event {
    data class AttackDeclared(
        val attackerId: CombatantId,
        val targetId: CombatantId,
        val label: String,
        val kind: AttackKind,
        val cost: Cost,
        val costNote: String?,
        val roll: Int,
        val rollMode: RollMode,
        val natural: Natural?,
        val early: Boolean,
    ) : Event

    data class AttackResolved(val hit: Boolean) : Event

    data class AttackSentBack(val reason: String) : Event

    data class AttackResubmitted(val roll: Int, val rollMode: RollMode, val natural: Natural?) : Event

    data class ReactionUsed(
        val combatantId: CombatantId,
        val name: String,
        val acBonus: Int,
        val automatic: Boolean,
    ) : Event

    data object ReactionSkipped : Event

    data class DamageSubmitted(val amount: Int, val damageType: String) : Event

    data class DamageApplied(val targetId: CombatantId, val amount: Int, val damageType: String) : Event

    data class StandardActionUsed(val combatantId: CombatantId, val label: String, val cost: Cost) : Event

    data class TurnAdvanced(val nextId: CombatantId, val round: Int) : Event
}
