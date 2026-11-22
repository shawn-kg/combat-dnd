package app.combatdnd.encounter.domain

/** Something that happened. Stored in encounter_event and folded with `evolve`. */
sealed interface Event {
    data class AttackLogged(
        val id: AttackId,
        val round: Int,
        val attackerId: CombatantId,
        val targetId: CombatantId,
        val hit: Boolean,
        val damage: Int,
    ) : Event

    data class AttackEdited(val id: AttackId, val hit: Boolean, val damage: Int) : Event

    data class AttackDeleted(val id: AttackId) : Event

    data class TurnChanged(val currentId: CombatantId, val round: Int) : Event

    data class CombatantAdded(val combatant: Combatant) : Event

    data class CombatantRemoved(val id: CombatantId) : Event

    data class CombatantRevealed(val id: CombatantId) : Event

    data class DefeatedSet(val id: CombatantId, val defeated: Boolean) : Event

    data class CombatantEdited(
        val id: CombatantId,
        val name: String?,
        val portraitKey: String?,
        val initiative: Int?,
        val ac: Int?,
        val hp: Int?,
        val maxHp: Int?,
    ) : Event

    data class Reordered(val order: List<CombatantId>) : Event
}
