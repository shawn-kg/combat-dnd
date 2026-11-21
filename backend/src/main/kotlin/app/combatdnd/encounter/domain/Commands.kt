package app.combatdnd.encounter.domain

/** Something an actor is trying to do. `decide` turns it into events or a rejection. */
sealed interface Command {
    data class DeclareAttack(
        val targetId: CombatantId,
        val label: String,
        val kind: AttackKind = AttackKind.ATTACK,
        val cost: Cost,
        val costNote: String? = null,
        val roll: Int,
        val rollMode: RollMode = RollMode.NORMAL,
        val natural: Natural? = null,
        val early: Boolean = false,
    ) : Command

    /** DM: hit (or failed save) / miss (or saved). */
    data class ResolveAttack(val hit: Boolean) : Command

    /** DM uses the target enemy's reaction (e.g. Parry) before calling the attack. */
    data class EnemyReact(val reaction: String) : Command

    /** DM returns the pending step to the attacker with a reason. */
    data class SendBack(val reason: String) : Command

    /** Attacker answers a send-back with a new roll. */
    data class Resubmit(
        val roll: Int,
        val rollMode: RollMode = RollMode.NORMAL,
        val natural: Natural? = null,
    ) : Command

    /** Target (or DM) uses a reaction in the reaction window. Unknown names are logged as-is. */
    data class React(val reaction: String) : Command

    data object SkipReaction : Command

    data class SubmitDamage(val amount: Int, val damageType: String) : Command

    /** DM applies damage, possibly adjusted. */
    data class ApplyDamage(val amount: Int) : Command

    /** A reaction outside the attack flow, e.g. an opportunity attack. */
    data class UseReaction(val combatantId: CombatantId, val label: String) : Command

    /** Dash, Disengage, Dodge, Help, Hide, Ready, Search, Use an Object, or a character option such as Second Wind. */
    data class UseStandardAction(val label: String, val cost: Cost) : Command

    data object NextTurn : Command
}
