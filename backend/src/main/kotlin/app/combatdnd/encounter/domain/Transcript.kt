package app.combatdnd.encounter.domain

/** One line of a fight's story. `turn` lines start a turn; the rest sit under the turn they happened in. */
data class TranscriptLine(val round: Int, val text: String, val turn: Boolean = false)

/** One attack as it finally stands (corrections applied, deleted ones gone), with the target's HP right after it. */
data class TranscriptAttack(
    val round: Int,
    val attacker: String,
    val target: String,
    val hit: Boolean,
    val damage: Int,
    val targetHp: Int,
    val targetMaxHp: Int,
    val corrected: Boolean,
)

data class Transcript(val lines: List<TranscriptLine>, val attacks: List<TranscriptAttack>, val final: EncounterState)

/**
 * Replays a fight's events (undone ones already left out) and writes down what each one did, in plain words,
 * with HP as it was at that moment. This is the history, so corrections show up as corrections. Setup before
 * round 1 (adding combatants, rolling initiative) is summed up as the starting order instead of line by line.
 */
fun transcript(events: List<Event>): Transcript {
    var state = EncounterState(round = 0, order = emptyList(), currentId = null, combatants = emptyMap())
    val lines = mutableListOf<TranscriptLine>()
    val attacks = linkedMapOf<AttackId, TranscriptAttack>()

    for (event in events) {
        val before = state
        val after = evolve(before, event)
        val started = before.currentId != null || before.round > 0
        fun name(id: CombatantId) = before.combatants[id]?.name ?: after.combatants[id]?.name ?: "Someone"
        fun hpOf(s: EncounterState, id: CombatantId) = s.combatants[id]?.let { "${it.hp}/${it.maxHp}" } ?: "removed"
        fun line(text: String) = lines.add(TranscriptLine(after.round, text))

        when (event) {
            is Event.TurnChanged -> {
                if (!started) {
                    val order = after.order.map(after::combatant).joinToString(" · ") { c ->
                        val tags = listOfNotNull(c.side.takeIf { it != Side.PARTY }?.label(), "hidden".takeIf { c.hidden }).joinToString(", ")
                        "${c.name} ${c.initiative}" + if (tags.isEmpty()) "" else " ($tags)"
                    }
                    line("Initiative order: $order")
                }
                lines.add(TranscriptLine(event.round, "${name(event.currentId)}’s turn", turn = true))
            }

            is Event.AttackLogged -> {
                val target = after.combatant(event.targetId)
                line("${name(event.attackerId)} → ${target.name}: ${result(event.hit, event.damage)} (${target.name} ${hpOf(after, target.id)})")
                attacks[event.id] = TranscriptAttack(
                    event.round, name(event.attackerId), target.name, event.hit, event.damage, target.hp, target.maxHp, corrected = false,
                )
            }

            is Event.AttackEdited -> {
                val entry = before.attack(event.id)!!
                val t = name(entry.targetId)
                line("Correction: ${name(entry.attackerId)} → $t is now ${result(event.hit, event.damage)} ($t ${hpOf(after, entry.targetId)})")
                attacks[event.id]?.let { a ->
                    val target = after.combatants[entry.targetId]
                    attacks[event.id] = a.copy(
                        hit = event.hit, damage = event.damage, corrected = true,
                        targetHp = target?.hp ?: a.targetHp, targetMaxHp = target?.maxHp ?: a.targetMaxHp,
                    )
                }
            }

            is Event.AttackDeleted -> {
                val entry = before.attack(event.id)!!
                line("Entry removed: ${name(entry.attackerId)} → ${name(entry.targetId)} (${result(entry.hit, entry.damage)})")
                attacks.remove(event.id)
            }

            is Event.CombatantAdded -> if (started) {
                val c = event.combatant
                line("${c.name} joined the fight (${c.side.label()}, initiative ${c.initiative}${if (c.hidden) ", hidden" else ""})")
            }

            is Event.CombatantRemoved -> if (started) line("${name(event.id)} left the fight")
            is Event.CombatantRevealed -> if (started) line("${name(event.id)} revealed to players")
            is Event.DefeatedSet -> if (started) line(if (event.defeated) "${name(event.id)} defeated" else "${name(event.id)} back in the fight")

            is Event.CombatantEdited -> if (started) {
                val old = before.combatant(event.id)
                val new = after.combatant(event.id)
                if (new.name != old.name) line("${old.name} renamed to ${new.name}")
                if (new.hp != old.hp || new.maxHp != old.maxHp) line("${new.name}’s HP set to ${new.hp}/${new.maxHp} (was ${old.hp}/${old.maxHp})")
                if (new.ac != old.ac) line("${new.name}’s AC set to ${new.ac} (was ${old.ac})")
                if (new.initiative != old.initiative) line("${new.name} moved to initiative ${new.initiative}")
                if (new.statsVisible != old.statsVisible) {
                    line(if (new.statsVisible) "${new.name}’s AC and HP shown to players" else "${new.name}’s AC and HP hidden from players")
                }
            }

            is Event.Reordered -> if (started) line("Turn order changed")
            is Event.InitiativeRolled, is Event.InitiativesSet -> if (started) line("Initiative changed")
        }
        state = after
    }
    return Transcript(lines, attacks.values.toList(), state)
}

private fun result(hit: Boolean, damage: Int) = if (hit) "hit, $damage damage" else "miss"

fun Side.label() = when (this) {
    Side.PARTY -> "party"
    Side.ENEMY -> "enemy"
    Side.NPC -> "NPC"
}
