package app.combatdnd.encounter.domain

/** Apply one event. Pure: the current state is `events.fold(initial, ::evolve)`. */
fun evolve(state: EncounterState, event: Event): EncounterState {
    val next = when (event) {
        is Event.AttackLogged -> {
            val target = state.combatant(event.targetId)
            val applied = if (event.hit) minOf(event.damage, target.hp) else 0
            state
                .update(target.id) { it.copy(hp = it.hp - applied) }
                .copy(
                    attacks = state.attacks + AttackEntry(
                        event.id, event.round, event.attackerId, event.targetId, event.hit, event.damage, applied,
                    ),
                )
        }

        is Event.AttackEdited -> {
            val entry = state.attack(event.id)!!
            val restored = state.giveBack(entry)
            val target = restored.combatants[entry.targetId]
            val applied = if (event.hit && target != null) minOf(event.damage, target.hp) else 0
            val withHp = if (target != null) restored.update(target.id) { it.copy(hp = it.hp - applied) } else restored
            withHp.updateAttack(entry.id) { it.copy(hit = event.hit, damage = event.damage, applied = applied) }
        }

        is Event.AttackDeleted -> {
            val entry = state.attack(event.id)!!
            state.giveBack(entry).updateAttack(entry.id) { it.copy(deleted = true, applied = 0) }
        }

        is Event.TurnChanged -> state.copy(currentId = event.currentId, round = event.round)

        is Event.CombatantAdded -> {
            val added = state.copy(
                combatants = state.combatants + (event.combatant.id to event.combatant),
                order = state.order + event.combatant.id,
            )
            added.copy(order = sortByInitiative(added))
        }

        is Event.CombatantRemoved -> {
            val handOff = if (state.currentId == event.id) nextTurn(state)?.takeIf { it.first != event.id } else null
            state.copy(
                combatants = state.combatants - event.id,
                order = state.order - event.id,
                currentId = if (state.currentId == event.id) handOff?.first else state.currentId,
                round = handOff?.second ?: state.round,
            )
        }

        is Event.CombatantRevealed -> state.update(event.id) { it.copy(hidden = false) }

        is Event.DefeatedSet -> state.update(event.id) { it.copy(defeated = event.defeated) }

        is Event.CombatantEdited -> {
            val edited = state.update(event.id) { c ->
                val maxHp = event.maxHp ?: c.maxHp
                c.copy(
                    name = event.name ?: c.name,
                    portraitKey = event.portraitKey ?: c.portraitKey,
                    initiative = event.initiative ?: c.initiative,
                    ac = event.ac ?: c.ac,
                    maxHp = maxHp,
                    hp = (event.hp ?: c.hp).coerceAtMost(maxHp),
                )
            }
            if (event.initiative != null) edited.copy(order = sortByInitiative(edited)) else edited
        }

        is Event.Reordered -> state.copy(order = event.order)
    }
    return next.copy(version = state.version + 1)
}

/** Undo an entry's effect on HP. A target that has since been removed is skipped. */
private fun EncounterState.giveBack(entry: AttackEntry): EncounterState {
    if (entry.applied == 0 || entry.targetId !in combatants) return this
    return update(entry.targetId) { it.copy(hp = minOf(it.maxHp, it.hp + entry.applied)) }
}
