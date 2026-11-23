package app.combatdnd.encounter.domain

/** Something an actor is trying to do. `decide` turns it into events or a rejection. */
sealed interface Command {
    /** Log one attack. On a hit, the damage comes off the target's HP right away. */
    data class LogAttack(
        val attackerId: CombatantId,
        val targetId: CombatantId,
        val hit: Boolean,
        val damage: Int = 0,
    ) : Command

    /** Correct a logged attack. HP is adjusted by the difference. */
    data class EditAttack(val attackId: AttackId, val hit: Boolean, val damage: Int) : Command

    /** Remove a logged attack. Its damage is given back. */
    data class DeleteAttack(val attackId: AttackId) : Command

    data object NextTurn : Command

    data object PreviousTurn : Command

    /** DM only from here down. */
    data class AddCombatant(val combatant: Combatant) : Command

    /** Several at once ("Goblin × 3"), so one Undo removes them all. */
    data class AddCombatants(val combatants: List<Combatant>) : Command

    data class RemoveCombatant(val id: CombatantId) : Command

    data class RevealCombatant(val id: CombatantId) : Command

    data class SetDefeated(val id: CombatantId, val defeated: Boolean) : Command

    /** Any field the DM wants to change. Null means "leave as is". Changing initiative re-sorts the list. */
    data class EditCombatant(
        val id: CombatantId,
        val name: String? = null,
        val portraitKey: String? = null,
        val initiative: Int? = null,
        val ac: Int? = null,
        val hp: Int? = null,
        val maxHp: Int? = null,
    ) : Command

    /** Manual order, e.g. to break an initiative tie. Must list every combatant once. */
    data class Reorder(val order: List<CombatantId>) : Command
}
