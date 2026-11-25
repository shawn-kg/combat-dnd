package app.combatdnd.encounter.domain

/** What one viewer is allowed to see. Fields a viewer may not see are null, never just hidden in the UI. */
data class EncounterView(
    /** True for the DM; the client uses it to decide which controls to show (the server still checks every command). */
    val viewerIsDm: Boolean,
    val round: Int,
    val currentId: CombatantId?,
    val version: Long,
    val combatants: List<CombatantView>,
    /** Every visible entry, newest first, for the DM's turn log and for fixing your own entries. */
    val attacks: List<AttackView>,
)

data class CombatantView(
    val id: CombatantId,
    val name: String,
    val side: Side,
    val portraitKey: String?,
    val initiative: Int,
    val isCurrent: Boolean,
    val controlledByViewer: Boolean,
    /** A player runs it (vs. the DM), e.g. to show who still has to send their initiative. */
    val runByPlayer: Boolean,
    val ac: Int?,
    val hp: Int?,
    val maxHp: Int?,
    val hidden: Boolean,
    val defeated: Boolean,
    val statsVisible: Boolean,
    val lastTurn: LastTurnView?,
    /** This fight so far, from the attacks this viewer can see. */
    val totals: Totals,
)

/**
 * Damage counts as logged: a 12-damage hit on a 7 HP goblin is 12 dealt and 12 taken, even though HP stops at 0.
 * Misses count as attacks with no damage.
 */
data class Totals(val dealt: Int, val taken: Int, val hits: Int, val misses: Int, val biggestHit: Int) {
    companion object {
        val NONE = Totals(0, 0, 0, 0, 0)

        fun of(id: CombatantId, attacks: List<AttackEntry>): Totals {
            val made = attacks.filter { it.attackerId == id }
            return Totals(
                dealt = made.sumOf { it.damage },
                taken = attacks.filter { it.targetId == id }.sumOf { it.damage },
                hits = made.count { it.hit },
                misses = made.count { !it.hit },
                biggestHit = made.filter { it.hit }.maxOfOrNull { it.damage } ?: 0,
            )
        }
    }
}

/** The row's last-turn line, e.g. "Hit Goblin 2 · 9" or "2 hits, 1 miss · 17". */
data class LastTurnView(val round: Int, val hits: Int, val misses: Int, val damage: Int, val attacks: List<AttackView>)

data class AttackView(
    val id: AttackId,
    val round: Int,
    val attackerId: CombatantId,
    val targetId: CombatantId,
    /** Null when the target has been removed or is hidden from this viewer. */
    val targetName: String?,
    val hit: Boolean,
    val damage: Int,
)

fun viewFor(state: EncounterState, viewer: Actor): EncounterView {
    val isDm = viewer == Actor.Dm
    fun visible(id: CombatantId) = state.combatants[id]?.let { isDm || !it.hidden } ?: false

    fun attackView(a: AttackEntry) = AttackView(
        a.id, a.round, a.attackerId, a.targetId,
        targetName = state.combatants[a.targetId]?.takeIf { visible(it.id) }?.name,
        hit = a.hit, damage = a.damage,
    )

    // Totals come only from attacks this viewer may see, so they can't reveal what a hidden combatant did.
    val seenAttacks = state.attacks.filter { !it.deleted && visible(it.attackerId) }

    val combatants = state.order
        .map(state::combatant)
        .filter { visible(it.id) }
        .map { c ->
            val seesStats = isDm || c.side == Side.PARTY || (c.side == Side.NPC && c.statsVisible)
            CombatantView(
                id = c.id,
                name = c.name,
                side = c.side,
                portraitKey = c.portraitKey,
                initiative = c.initiative,
                isCurrent = c.id == state.currentId,
                controlledByViewer = !isDm && viewer.controls(c),
                runByPlayer = c.controller is Controller.Player,
                ac = if (seesStats) c.ac else null,
                hp = if (seesStats) c.hp else null,
                maxHp = if (seesStats) c.maxHp else null,
                hidden = c.hidden,
                defeated = c.defeated,
                statsVisible = c.statsVisible,
                lastTurn = state.lastTurn(c.id).takeIf { it.isNotEmpty() }?.let { entries ->
                    LastTurnView(
                        round = entries.first().round,
                        hits = entries.count { it.hit },
                        misses = entries.count { !it.hit },
                        damage = entries.sumOf { it.damage },
                        attacks = entries.map(::attackView),
                    )
                },
                totals = Totals.of(c.id, seenAttacks),
            )
        }

    val attacks = state.attacks
        .filter { !it.deleted && visible(it.attackerId) }
        .map(::attackView)
        .reversed()

    return EncounterView(isDm, state.round, state.currentId?.takeIf(::visible), state.version, combatants, attacks)
}
