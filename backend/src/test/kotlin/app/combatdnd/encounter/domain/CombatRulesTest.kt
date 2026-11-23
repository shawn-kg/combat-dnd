package app.combatdnd.encounter.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val LYRA_PLAYER = PlayerId("p-lyra")
private val BRAM_PLAYER = PlayerId("p-bram")
private val lyraActor = Actor.Player(LYRA_PLAYER)
private val bramActor = Actor.Player(BRAM_PLAYER)

private val BOSS = CombatantId("boss")
private val LYRA = CombatantId("lyra")
private val GOB1 = CombatantId("gob1")
private val ARCHER = CombatantId("archer")
private val BRAM = CombatantId("bram")

/** A goblin ambush in round 3, on Lyra's turn. */
private fun ambush(current: CombatantId? = LYRA): EncounterState {
    val cs = listOf(
        Combatant(BOSS, "Goblin Boss", Side.ENEMY, Controller.Dm, initiative = 21, ac = 15, hp = 22, maxHp = 30, portraitKey = "img/boss.webp"),
        Combatant(LYRA, "Lyra", Side.PARTY, Controller.Player(LYRA_PLAYER), initiative = 15, ac = 15, hp = 22, maxHp = 27),
        Combatant(GOB1, "Goblin 1", Side.ENEMY, Controller.Dm, initiative = 12, ac = 13, hp = 7, maxHp = 7),
        Combatant(ARCHER, "Goblin Archer", Side.ENEMY, Controller.Dm, initiative = 11, ac = 14, hp = 9, maxHp = 9, hidden = true),
        Combatant(BRAM, "Bram", Side.PARTY, Controller.Player(BRAM_PLAYER), initiative = 9, ac = 16, hp = 19, maxHp = 31),
    )
    return EncounterState(round = 3, order = cs.map { it.id }, currentId = current, combatants = cs.associateBy { it.id })
}

/** Runs commands in order, failing loudly if any is rejected. */
private fun EncounterState.run(vararg steps: Pair<Actor, Command>): EncounterState =
    steps.fold(this) { s, (actor, cmd) ->
        when (val d = decide(s, cmd, actor)) {
            is Decision.Accepted -> d.events.fold(s, ::evolve)
            is Decision.Rejected -> error("Rejected $cmd: ${d.message}")
        }
    }

private fun hit(attacker: CombatantId, target: CombatantId, damage: Int) = Command.LogAttack(attacker, target, hit = true, damage = damage)
private fun miss(attacker: CombatantId, target: CombatantId) = Command.LogAttack(attacker, target, hit = false)

class LoggingTest {
    @Test
    fun `a hit takes the damage off the target's HP`() {
        val s = ambush().run(lyraActor to hit(LYRA, BOSS, 9))
        assertEquals(13, s.combatant(BOSS).hp)
    }

    @Test
    fun `a miss changes nothing and records no damage`() {
        val s = ambush().run(lyraActor to Command.LogAttack(LYRA, BOSS, hit = false, damage = 9))
        assertEquals(22, s.combatant(BOSS).hp)
        assertEquals(0, s.attacks.single().damage)
    }

    @Test
    fun `HP never goes below 0`() {
        val s = ambush().run(lyraActor to hit(LYRA, GOB1, 12))
        assertEquals(0, s.combatant(GOB1).hp)
    }

    @Test
    fun `players log attacks only for their own character on its turn`() {
        assertIs<Decision.Rejected>(decide(ambush(), hit(BRAM, BOSS, 5), bramActor)) // not Bram's turn
        assertIs<Decision.Rejected>(decide(ambush(), hit(LYRA, BOSS, 5), bramActor)) // not Bram's character
        assertIs<Decision.Rejected>(decide(ambush(), hit(LYRA, ARCHER, 5), lyraActor)) // hidden target
    }

    @Test
    fun `the DM can log for anyone at any time`() {
        val s = ambush().run(Actor.Dm to hit(GOB1, BRAM, 4), Actor.Dm to hit(BRAM, GOB1, 3))
        assertEquals(15, s.combatant(BRAM).hp)
        assertEquals(4, s.combatant(GOB1).hp)
    }
}

class LastTurnTest {
    @Test
    fun `the last turn sums every attack from the latest round`() {
        val s = ambush().run(
            lyraActor to hit(LYRA, BOSS, 9),
            lyraActor to hit(LYRA, BOSS, 8),
            lyraActor to miss(LYRA, GOB1),
        )
        val row = viewFor(s, lyraActor).combatants.single { it.id == LYRA }.lastTurn
        assertNotNull(row)
        assertEquals(2, row.hits)
        assertEquals(1, row.misses)
        assertEquals(17, row.damage)
    }

    @Test
    fun `an older round is replaced once the combatant logs in a newer one`() {
        val round3 = ambush().run(lyraActor to hit(LYRA, BOSS, 9))
        val round4 = round3.copy(round = 4).run(lyraActor to miss(LYRA, GOB1))
        val last = round4.lastTurn(LYRA)
        assertEquals(1, last.size)
        assertEquals(4, last.single().round)
    }

    @Test
    fun `a combatant that never attacked has no last turn`() {
        assertNull(viewFor(ambush(), Actor.Dm).combatants.single { it.id == BRAM }.lastTurn)
    }
}

class CorrectionsTest {
    @Test
    fun `editing an attack adjusts HP by the difference`() {
        val s = ambush().run(lyraActor to hit(LYRA, BOSS, 9))
        val id = s.attacks.single().id
        val fixed = s.run(Actor.Dm to Command.EditAttack(id, hit = true, damage = 4))
        assertEquals(18, fixed.combatant(BOSS).hp)

        val missed = fixed.run(Actor.Dm to Command.EditAttack(id, hit = false, damage = 0))
        assertEquals(22, missed.combatant(BOSS).hp)
    }

    @Test
    fun `deleting an attack gives back exactly the HP it took`() {
        val s = ambush().run(lyraActor to hit(LYRA, GOB1, 12)) // overkill: only 7 applied
        val restored = s.run(Actor.Dm to Command.DeleteAttack(s.attacks.single().id))
        assertEquals(7, restored.combatant(GOB1).hp)
        assertNull(viewFor(restored, Actor.Dm).combatants.single { it.id == LYRA }.lastTurn)
    }

    @Test
    fun `players can fix their own entries during their turn, but not afterwards`() {
        val s = ambush().run(lyraActor to hit(LYRA, BOSS, 9))
        val id = s.attacks.single().id
        assertIs<Decision.Accepted>(decide(s, Command.EditAttack(id, true, 6), lyraActor))
        assertIs<Decision.Rejected>(decide(s, Command.EditAttack(id, true, 6), bramActor))

        val later = s.run(lyraActor to Command.NextTurn)
        assertIs<Decision.Rejected>(decide(later, Command.DeleteAttack(id), lyraActor))
        assertIs<Decision.Accepted>(decide(later, Command.DeleteAttack(id), Actor.Dm))
    }
}

class TurnOrderTest {
    @Test
    fun `next turn skips hidden and defeated combatants and wraps the round`() {
        val s = ambush(current = GOB1).run(Actor.Dm to Command.NextTurn)
        assertEquals(BRAM, s.currentId) // the archer is hidden

        val wrapped = s.run(bramActor to Command.NextTurn)
        assertEquals(BOSS, wrapped.currentId)
        assertEquals(4, wrapped.round)

        val bossDown = wrapped.run(Actor.Dm to Command.SetDefeated(BOSS, true), Actor.Dm to Command.PreviousTurn)
        assertEquals(BRAM, bossDown.currentId)
        assertEquals(3, bossDown.round)
    }

    @Test
    fun `only the DM or the current player can end a turn`() {
        assertIs<Decision.Rejected>(decide(ambush(), Command.NextTurn, bramActor))
        assertIs<Decision.Accepted>(decide(ambush(), Command.NextTurn, lyraActor))
        assertIs<Decision.Rejected>(decide(ambush(), Command.PreviousTurn, lyraActor))
    }

    @Test
    fun `the first next turn starts combat at the top of the order`() {
        val s = ambush(current = null).copy(round = 0).run(Actor.Dm to Command.NextTurn)
        assertEquals(BOSS, s.currentId)
        assertEquals(1, s.round)
    }
}

class DmEditingTest {
    @Test
    fun `the DM can edit any field, and initiative re-sorts the list`() {
        val s = ambush().run(
            Actor.Dm to Command.EditCombatant(BRAM, name = "Bram the Bold", ac = 18, hp = 25, initiative = 20, portraitKey = "img/bram.webp"),
        )
        val bram = s.combatant(BRAM)
        assertEquals("Bram the Bold", bram.name)
        assertEquals(18, bram.ac)
        assertEquals(25, bram.hp)
        assertEquals("img/bram.webp", bram.portraitKey)
        assertEquals(listOf(BOSS, BRAM, LYRA, GOB1, ARCHER), s.order)
    }

    @Test
    fun `players cannot edit, add, reveal or reorder`() {
        val gob = Combatant(CombatantId("gob2"), "Goblin 2", Side.ENEMY, Controller.Dm, 12, 13, 7, 7)
        listOf(
            Command.EditCombatant(LYRA, hp = 27),
            Command.AddCombatant(gob),
            Command.RevealCombatant(ARCHER),
            Command.Reorder(ambush().order.reversed()),
        ).forEach { assertIs<Decision.Rejected>(decide(ambush(), it, lyraActor)) }
    }

    @Test
    fun `new enemies slot into initiative order`() {
        val gob = Combatant(CombatantId("gob2"), "Goblin 2", Side.ENEMY, Controller.Dm, initiative = 16, ac = 13, hp = 7, maxHp = 7)
        val s = ambush().run(Actor.Dm to Command.AddCombatant(gob))
        assertEquals(listOf(BOSS, gob.id, LYRA, GOB1, ARCHER, BRAM), s.order)
    }

    @Test
    fun `several enemies are added as one command, so one undo removes them all`() {
        val wolves = (1..3).map { Combatant(CombatantId("wolf$it"), "Wolf $it", Side.ENEMY, Controller.Dm, 17, 13, 11, 11) }
        val d = decide(ambush(), Command.AddCombatants(wolves), Actor.Dm)
        assertIs<Decision.Accepted>(d)
        assertEquals(3, d.events.size)
        val s = d.events.fold(ambush(), ::evolve)
        assertEquals(listOf(BOSS, CombatantId("wolf1"), CombatantId("wolf2"), CombatantId("wolf3"), LYRA), s.order.take(5))

        assertIs<Decision.Rejected>(decide(ambush(), Command.AddCombatants(wolves), lyraActor))
        assertIs<Decision.Rejected>(decide(s, Command.AddCombatants(wolves.take(1)), Actor.Dm)) // id already used
    }

    @Test
    fun `revealing makes a hidden enemy visible and able to take turns`() {
        val s = ambush(current = GOB1).run(Actor.Dm to Command.RevealCombatant(ARCHER), Actor.Dm to Command.NextTurn)
        assertEquals(ARCHER, s.currentId)
        assertTrue(viewFor(s, lyraActor).combatants.any { it.id == ARCHER })
    }

    @Test
    fun `removing the current combatant hands the turn on`() {
        val s = ambush().run(Actor.Dm to Command.RemoveCombatant(LYRA))
        assertEquals(GOB1, s.currentId)
        assertTrue(LYRA !in s.order)
    }
}

class VisibilityTest {
    @Test
    fun `players never receive enemy AC or HP, or hidden enemies`() {
        val view = viewFor(ambush(), lyraActor)
        val boss = view.combatants.single { it.id == BOSS }
        assertNull(boss.ac)
        assertNull(boss.hp)
        assertNull(boss.maxHp)
        assertEquals("img/boss.webp", boss.portraitKey)
        assertTrue(view.combatants.none { it.id == ARCHER })

        val bram = view.combatants.single { it.id == BRAM }
        assertEquals(19, bram.hp) // the party sees each other's numbers
        assertTrue(view.combatants.single { it.id == LYRA }.controlledByViewer)
    }

    @Test
    fun `the DM sees everything`() {
        val view = viewFor(ambush(), Actor.Dm)
        assertEquals(22, view.combatants.single { it.id == BOSS }.hp)
        assertNotNull(view.combatants.singleOrNull { it.id == ARCHER })
    }

    @Test
    fun `players don't learn hidden enemies' names or attacks through the log`() {
        val s = ambush().run(Actor.Dm to hit(ARCHER, BRAM, 5), Actor.Dm to hit(LYRA, ARCHER, 3))
        val forLyra = viewFor(s, lyraActor)
        assertTrue(forLyra.attacks.none { it.attackerId == ARCHER })
        assertNull(forLyra.attacks.single { it.attackerId == LYRA }.targetName)
        assertEquals(14, forLyra.combatants.single { it.id == BRAM }.hp) // the damage itself still shows
    }
}

class ReplayTest {
    @Test
    fun `folding the same events always gives the same state`() {
        val start = ambush()
        val commands = listOf(
            lyraActor to hit(LYRA, BOSS, 9),
            Actor.Dm to Command.EditAttack(AttackId("a1"), hit = true, damage = 6),
            lyraActor to Command.NextTurn,
            Actor.Dm to hit(GOB1, BRAM, 4),
        )
        val events = mutableListOf<Event>()
        var s = start
        for ((actor, cmd) in commands) {
            val d = decide(s, cmd, actor) as Decision.Accepted
            events += d.events
            s = d.events.fold(s, ::evolve)
        }
        assertEquals(s, events.fold(start, ::evolve))
        assertEquals(events.size.toLong(), s.version)
    }
}
