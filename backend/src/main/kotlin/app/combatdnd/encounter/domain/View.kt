package app.combatdnd.encounter.domain

/** What one viewer is allowed to see. Fields a viewer may not see are null, never just hidden in the UI. */
data class EncounterView(
    val round: Int,
    val currentId: CombatantId,
    val version: Long,
    val combatants: List<CombatantView>,
    val pending: PendingView?,
)

data class CombatantView(
    val id: CombatantId,
    val name: String,
    val side: Side,
    val initiative: Int,
    val isCurrent: Boolean,
    val controlledByViewer: Boolean,
    val hp: Int?,
    val maxHp: Int?,
    val ac: Int?,
    val conditions: List<Condition>,
    val resources: Resources?,
    val hidden: Boolean,
    val defeated: Boolean,
)

data class PendingView(
    val attackerId: CombatantId,
    val targetId: CombatantId,
    val label: String,
    val kind: AttackKind,
    val roll: Int,
    val rollMode: RollMode,
    val natural: Natural?,
    val early: Boolean,
    val stage: AttackStage,
    val damage: Int?,
    val damageType: String?,
    val sendBackReason: String?,
    /** Null when the viewer may not see the target's AC. */
    val targetAc: Int?,
)

fun viewFor(state: EncounterState, viewer: Actor): EncounterView {
    val isDm = viewer == Actor.Dm
    fun seesStats(c: Combatant) = isDm || c.side != Side.ENEMY

    val combatants = state.order
        .map(state::combatant)
        .filter { isDm || !it.hidden }
        .map { c ->
            val stats = seesStats(c)
            CombatantView(
                id = c.id,
                name = c.name,
                side = c.side,
                initiative = c.initiative,
                isCurrent = c.id == state.currentId,
                controlledByViewer = !isDm && viewer.controls(c),
                hp = if (stats) c.hp else null,
                maxHp = if (stats) c.maxHp else null,
                ac = if (stats) c.effectiveAc else null,
                conditions = visibleConditions(c, viewer),
                resources = if (stats) c.resources else null,
                hidden = c.hidden,
                defeated = c.defeated,
            )
        }

    val pending = state.pending?.let { p ->
        val target = state.combatant(p.targetId)
        PendingView(
            p.attackerId, p.targetId, p.label, p.kind, p.roll, p.rollMode, p.natural, p.early, p.stage,
            p.damage, p.damageType, p.sendBackReason,
            targetAc = if (seesStats(target)) target.effectiveAc else null,
        )
    }

    return EncounterView(state.round, state.currentId, state.version, combatants, pending)
}

/** Private conditions exist only on enemies, and only the DM sees them. */
fun visibleConditions(c: Combatant, viewer: Actor): List<Condition> =
    if (viewer == Actor.Dm || c.side != Side.ENEMY) c.conditions else c.conditions.filterNot { it.private }
