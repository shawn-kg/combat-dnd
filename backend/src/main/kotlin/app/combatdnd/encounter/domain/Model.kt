package app.combatdnd.encounter.domain

@JvmInline
value class CombatantId(val value: String)

@JvmInline
value class PlayerId(val value: String)

@JvmInline
value class AttackId(val value: String)

enum class Side { PARTY, ENEMY }

/** Who is sending a command. */
sealed interface Actor {
    data object Dm : Actor
    data class Player(val id: PlayerId) : Actor
}

/** Who controls a combatant: the player who claimed the character, or the DM. */
sealed interface Controller {
    data object Dm : Controller
    data class Player(val id: PlayerId) : Controller
}

data class Combatant(
    val id: CombatantId,
    val name: String,
    val side: Side,
    val controller: Controller,
    val initiative: Int,
    val ac: Int,
    val hp: Int,
    val maxHp: Int,
    /** Object-storage key of the portrait; null shows initials. */
    val portraitKey: String? = null,
    val hidden: Boolean = false,
    val defeated: Boolean = false,
)

/** One logged attack. `applied` is the HP actually removed (damage is capped at the target's HP). */
data class AttackEntry(
    val id: AttackId,
    val round: Int,
    val attackerId: CombatantId,
    val targetId: CombatantId,
    val hit: Boolean,
    val damage: Int,
    val applied: Int,
    val deleted: Boolean = false,
)

data class EncounterState(
    val round: Int,
    /** Combatant ids in initiative order. */
    val order: List<CombatantId>,
    val currentId: CombatantId?,
    val combatants: Map<CombatantId, Combatant>,
    val attacks: List<AttackEntry> = emptyList(),
    val version: Long = 0,
) {
    fun combatant(id: CombatantId): Combatant =
        combatants[id] ?: throw IllegalStateException("Unknown combatant ${id.value}")

    fun attack(id: AttackId): AttackEntry? = attacks.find { it.id == id }

    fun update(id: CombatantId, change: (Combatant) -> Combatant): EncounterState =
        copy(combatants = combatants + (id to change(combatant(id))))

    fun updateAttack(id: AttackId, change: (AttackEntry) -> AttackEntry): EncounterState =
        copy(attacks = attacks.map { if (it.id == id) change(it) else it })

    /** Next attack id. Deterministic, so replaying the same events gives the same ids. */
    fun nextAttackId(): AttackId = AttackId("a${attacks.size + 1}")

    /**
     * A combatant's last turn: its attacks in the latest round in which it logged any.
     * Derived from the log, so editing, deleting or undoing an attack updates it.
     */
    fun lastTurn(id: CombatantId): List<AttackEntry> {
        val own = attacks.filter { it.attackerId == id && !it.deleted }
        val latest = own.maxOfOrNull { it.round } ?: return emptyList()
        return own.filter { it.round == latest }
    }
}

fun Actor.controls(c: Combatant): Boolean = when (this) {
    Actor.Dm -> true
    is Actor.Player -> c.controller == Controller.Player(id)
}

/** Initiative order, highest first; ties keep their current relative order. */
fun sortByInitiative(state: EncounterState): List<CombatantId> =
    state.order.sortedByDescending { state.combatant(it).initiative }
