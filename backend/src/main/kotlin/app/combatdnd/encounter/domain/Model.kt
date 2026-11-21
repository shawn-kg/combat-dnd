package app.combatdnd.encounter.domain

@JvmInline
value class CombatantId(val value: String)

@JvmInline
value class PlayerId(val value: String)

enum class Side { PARTY, ENEMY, NEUTRAL }

/** What a declaration uses. Tracked, never enforced. */
enum class Cost { ACTION, BONUS, REACTION, FREE }

/** How the player rolled their physical dice. The app never rolls. */
enum class RollMode { NORMAL, ADVANTAGE, DISADVANTAGE }

enum class Natural { TWENTY, ONE }

enum class AttackKind { ATTACK, SPELL }

enum class ReactionMode { ASK, NEVER, AUTO_IF_TURNS_MISS }

enum class AttackStage { DECLARED, RESUBMIT, REACTION_WINDOW, AWAITING_DAMAGE, DAMAGE_SUBMITTED }

/** Who is sending a command. */
sealed interface Actor {
    data object Dm : Actor
    data class Player(val id: PlayerId) : Actor
}

/** Who controls a combatant. Enemies and NPC allies are DM-controlled. */
sealed interface Controller {
    data object Dm : Controller
    data class Player(val id: PlayerId) : Controller
}

data class Condition(val name: String, val private: Boolean = false)

data class ReactionDef(
    val name: String,
    val acBonus: Int = 0,
    val mode: ReactionMode = ReactionMode.ASK,
)

data class Resources(
    val actionUsed: Boolean = false,
    val bonusUsed: Boolean = false,
    val reactionUsed: Boolean = false,
    val movementUsed: Int = 0,
    val dashed: Boolean = false,
    val attacksThisTurn: Int = 0,
)

data class Combatant(
    val id: CombatantId,
    val name: String,
    val side: Side,
    val controller: Controller,
    val initiative: Int,
    val hp: Int,
    val maxHp: Int,
    val ac: Int,
    val speed: Int = 30,
    val acBonus: Int = 0,
    val conditions: List<Condition> = emptyList(),
    val reactions: List<ReactionDef> = emptyList(),
    /** A reminder only ("usually 2"), never a cap. */
    val attacksPerAction: Int = 1,
    val resources: Resources = Resources(),
    val hidden: Boolean = false,
    val defeated: Boolean = false,
) {
    val effectiveAc: Int get() = ac + acBonus
    val movementLimit: Int get() = if (resources.dashed) speed * 2 else speed

    /** Out of the fight: marked defeated, or an enemy at 0 HP. PCs at 0 HP still take turns (death saves). */
    val isDown: Boolean get() = defeated || (side == Side.ENEMY && hp == 0)
}

data class PendingAttack(
    val attackerId: CombatantId,
    val targetId: CombatantId,
    val label: String,
    val kind: AttackKind,
    val roll: Int,
    val rollMode: RollMode,
    val natural: Natural?,
    val early: Boolean,
    val stage: AttackStage,
    val damage: Int? = null,
    val damageType: String? = null,
    val sendBackReason: String? = null,
)

data class EncounterState(
    val round: Int,
    /** Combatant ids in initiative order. */
    val order: List<CombatantId>,
    val currentId: CombatantId,
    val combatants: Map<CombatantId, Combatant>,
    val pending: PendingAttack? = null,
    val version: Long = 0,
) {
    fun combatant(id: CombatantId): Combatant =
        combatants[id] ?: throw IllegalStateException("Unknown combatant ${id.value}")

    val current: Combatant get() = combatant(currentId)

    fun update(id: CombatantId, change: (Combatant) -> Combatant): EncounterState =
        copy(combatants = combatants + (id to change(combatant(id))))
}

fun Actor.controls(c: Combatant): Boolean = when (this) {
    Actor.Dm -> true
    is Actor.Player -> c.controller == Controller.Player(id)
}

fun Combatant.controlledBy(): Actor = when (val ctl = controller) {
    Controller.Dm -> Actor.Dm
    is Controller.Player -> Actor.Player(ctl.id)
}
