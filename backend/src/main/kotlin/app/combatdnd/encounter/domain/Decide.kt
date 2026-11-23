package app.combatdnd.encounter.domain

sealed interface Decision {
    data class Accepted(val events: List<Event>) : Decision
    data class Rejected(val reason: Reason, val message: String) : Decision
}

enum class Reason { FORBIDDEN, INVALID }

private fun ok(vararg events: Event) = Decision.Accepted(events.toList())
private fun forbidden(message: String) = Decision.Rejected(Reason.FORBIDDEN, message)
private fun invalid(message: String) = Decision.Rejected(Reason.INVALID, message)

private const val DM_ONLY = "Only the DM can do that."

/**
 * May this actor do this now, and what happens if so?
 *
 * The DM may do anything. A player may log attacks for their own character during its turn,
 * fix their own entries from that turn, and end their own turn.
 */
fun decide(state: EncounterState, cmd: Command, actor: Actor): Decision {
    val isDm = actor == Actor.Dm
    return when (cmd) {
        is Command.LogAttack -> {
            val attacker = state.combatants[cmd.attackerId] ?: return invalid("Unknown attacker.")
            val target = state.combatants[cmd.targetId] ?: return invalid("Unknown target.")
            when {
                !actor.controls(attacker) -> forbidden("That isn't your character.")
                !isDm && state.currentId != attacker.id -> forbidden("You can log attacks on your own turn.")
                !isDm && target.hidden -> invalid("Unknown target.")
                target.id == attacker.id -> invalid("Pick someone other than yourself.")
                cmd.damage < 0 -> invalid("Damage can't be negative.")
                else -> ok(
                    Event.AttackLogged(
                        id = state.nextAttackId(),
                        round = state.round,
                        attackerId = attacker.id,
                        targetId = target.id,
                        hit = cmd.hit,
                        damage = if (cmd.hit) cmd.damage else 0,
                    ),
                )
            }
        }

        is Command.EditAttack -> {
            val entry = state.attack(cmd.attackId)?.takeUnless { it.deleted } ?: return invalid("Unknown attack.")
            when {
                !canFix(state, entry, actor) -> forbidden("Only the DM can change that entry now.")
                cmd.damage < 0 -> invalid("Damage can't be negative.")
                else -> ok(Event.AttackEdited(entry.id, cmd.hit, if (cmd.hit) cmd.damage else 0))
            }
        }

        is Command.DeleteAttack -> {
            val entry = state.attack(cmd.attackId)?.takeUnless { it.deleted } ?: return invalid("Unknown attack.")
            if (!canFix(state, entry, actor)) forbidden("Only the DM can delete that entry now.")
            else ok(Event.AttackDeleted(entry.id))
        }

        Command.NextTurn -> {
            val current = state.currentId?.let(state::combatant)
            when {
                !isDm && (current == null || !actor.controls(current)) -> forbidden("It is not your turn.")
                else -> nextTurn(state)?.let { (id, round) -> ok(Event.TurnChanged(id, round)) }
                    ?: invalid("There is nobody to take a turn.")
            }
        }

        Command.PreviousTurn -> when {
            !isDm -> forbidden(DM_ONLY)
            else -> previousTurn(state)?.let { (id, round) -> ok(Event.TurnChanged(id, round)) }
                ?: invalid("There is no previous turn.")
        }

        is Command.AddCombatant -> when {
            !isDm -> forbidden(DM_ONLY)
            cmd.combatant.id in state.combatants -> invalid("That combatant is already in the encounter.")
            cmd.combatant.maxHp <= 0 -> invalid("Max HP must be above 0.")
            else -> ok(Event.CombatantAdded(cmd.combatant))
        }

        is Command.AddCombatants -> {
            val ids = cmd.combatants.map { it.id }
            when {
                !isDm -> forbidden(DM_ONLY)
                cmd.combatants.isEmpty() -> invalid("Add at least one.")
                ids.toSet().size != ids.size || ids.any { it in state.combatants } -> invalid("Each combatant needs its own id.")
                cmd.combatants.any { it.maxHp <= 0 } -> invalid("Max HP must be above 0.")
                else -> Decision.Accepted(cmd.combatants.map(Event::CombatantAdded))
            }
        }

        is Command.RemoveCombatant -> when {
            !isDm -> forbidden(DM_ONLY)
            cmd.id !in state.combatants -> invalid("Unknown combatant.")
            else -> ok(Event.CombatantRemoved(cmd.id))
        }

        is Command.RevealCombatant -> when {
            !isDm -> forbidden(DM_ONLY)
            cmd.id !in state.combatants -> invalid("Unknown combatant.")
            !state.combatant(cmd.id).hidden -> invalid("${state.combatant(cmd.id).name} is already visible.")
            else -> ok(Event.CombatantRevealed(cmd.id))
        }

        is Command.SetDefeated -> when {
            !isDm -> forbidden(DM_ONLY)
            cmd.id !in state.combatants -> invalid("Unknown combatant.")
            else -> ok(Event.DefeatedSet(cmd.id, cmd.defeated))
        }

        is Command.EditCombatant -> {
            val c = state.combatants[cmd.id]
            val maxHp = cmd.maxHp ?: c?.maxHp ?: 0
            when {
                !isDm -> forbidden(DM_ONLY)
                c == null -> invalid("Unknown combatant.")
                cmd.name != null && cmd.name.isBlank() -> invalid("Give it a name.")
                maxHp <= 0 -> invalid("Max HP must be above 0.")
                cmd.hp != null && cmd.hp !in 0..maxHp -> invalid("HP must be between 0 and $maxHp.")
                cmd.ac != null && cmd.ac < 0 -> invalid("AC can't be negative.")
                else -> ok(Event.CombatantEdited(cmd.id, cmd.name?.trim(), cmd.portraitKey, cmd.initiative, cmd.ac, cmd.hp, cmd.maxHp))
            }
        }

        is Command.Reorder -> when {
            !isDm -> forbidden(DM_ONLY)
            cmd.order.size != state.order.size || cmd.order.toSet() != state.order.toSet() ->
                invalid("The new order must list every combatant once.")
            else -> ok(Event.Reordered(cmd.order))
        }
    }
}

/** Players may fix their own entries during their own turn; the DM may fix anything. */
private fun canFix(state: EncounterState, entry: AttackEntry, actor: Actor): Boolean =
    actor == Actor.Dm ||
        (actor.controls(state.combatant(entry.attackerId)) && state.currentId == entry.attackerId && entry.round == state.round)

private fun EncounterState.takesTurns(id: CombatantId): Boolean {
    val c = combatant(id)
    return !c.hidden && !c.defeated
}

/** Next combatant in order, skipping hidden and defeated ones; the round goes up on wrap-around. */
internal fun nextTurn(state: EncounterState): Pair<CombatantId, Int>? {
    if (state.order.none(state::takesTurns)) return null
    val current = state.currentId ?: return state.order.first(state::takesTurns) to maxOf(state.round, 1)
    var index = state.order.indexOf(current)
    var round = state.round
    repeat(state.order.size) {
        index = (index + 1) % state.order.size
        if (index == 0) round++
        val id = state.order[index]
        if (state.takesTurns(id)) return id to round
    }
    return null
}

/** The DM stepping back a turn; the round goes down when stepping back past the top. */
internal fun previousTurn(state: EncounterState): Pair<CombatantId, Int>? {
    val current = state.currentId ?: return null
    var index = state.order.indexOf(current)
    var round = state.round
    repeat(state.order.size) {
        if (index == 0) {
            if (round <= 1) return null
            round--
        }
        index = (index - 1 + state.order.size) % state.order.size
        val id = state.order[index]
        if (state.takesTurns(id)) return id to round
    }
    return null
}
