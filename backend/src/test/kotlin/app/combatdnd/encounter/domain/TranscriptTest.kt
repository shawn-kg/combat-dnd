package app.combatdnd.encounter.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranscriptTest {
    private val lyra = CombatantId("lyra")
    private val gob = CombatantId("gob")
    private val spy = CombatantId("spy")

    private fun c(id: CombatantId, name: String, side: Side, init: Int, hp: Int, hidden: Boolean = false, player: String? = null) =
        Combatant(id, name, side, player?.let { Controller.Player(PlayerId(it)) } ?: Controller.Dm, init, 13, hp, hp, hidden = hidden)

    /** A short fight as events: setup, round 1 with a correction, a deleted entry, an HP edit, round 2. */
    private val events = listOf(
        Event.CombatantAdded(c(lyra, "Lyra", Side.PARTY, 0, 27, player = "p1")),
        Event.CombatantAdded(c(gob, "Goblin", Side.ENEMY, 0, 7)),
        Event.CombatantAdded(c(spy, "Spy", Side.NPC, 0, 10, hidden = true)),
        Event.InitiativesSet(mapOf(lyra to 15, gob to 12, spy to 5)),
        Event.TurnChanged(lyra, 1),
        Event.AttackLogged(AttackId("a1"), 1, lyra, gob, hit = true, damage = 4),
        Event.AttackEdited(AttackId("a1"), hit = true, damage = 12), // as logged: 12, though the goblin only had 7
        Event.TurnChanged(gob, 1),
        Event.AttackLogged(AttackId("a2"), 1, gob, lyra, hit = true, damage = 5),
        Event.AttackLogged(AttackId("a3"), 1, gob, lyra, hit = false, damage = 0),
        Event.AttackDeleted(AttackId("a3")),
        Event.CombatantEdited(lyra, null, null, null, null, 25, null),
        Event.DefeatedSet(gob, true),
        Event.TurnChanged(lyra, 2),
    )

    @Test
    fun `the replay tells the story in order, with HP as it was, and changes noted beside the attack`() {
        val t = transcript(events)
        assertEquals(
            listOf(
                "1: Initiative order: Lyra 15 · Goblin 12 (enemy) · Spy 5 (NPC, hidden)",
                "1: *Lyra’s turn",
                "1: Lyra → Goblin: hit, 4 damage (Goblin 3/7) [corrected in round 1: hit, 12 damage (Goblin 0/7)]",
                "1: *Goblin’s turn",
                "1: Goblin → Lyra: hit, 5 damage (Lyra 22/27)",
                "1: Goblin → Lyra: miss (Lyra 22/27) [removed in round 1]",
                "1: Lyra’s HP set to 25/27 (was 22/27)",
                "1: Goblin defeated",
                "2: *Lyra’s turn",
            ),
            t.lines.map(::show),
        )
        // The attack list is the final word: corrected values, deleted entries gone.
        assertEquals(listOf("Lyra→Goblin hit 12 (0/7) corrected", "Goblin→Lyra hit 5 (22/27)"), t.attacks.map {
            "${it.attacker}→${it.target} ${if (it.hit) "hit" else "miss"} ${it.damage} (${it.targetHp}/${it.targetMaxHp})" + if (it.corrected) " corrected" else ""
        })
    }

    @Test
    fun `fixes made later stay beside the attack, each one noted in order with the round it happened in`() {
        val t = transcript(
            listOf(
                Event.CombatantAdded(c(lyra, "Lyra", Side.PARTY, 15, 27)),
                Event.CombatantAdded(c(gob, "Goblin", Side.ENEMY, 12, 7)),
                Event.TurnChanged(lyra, 1),
                Event.AttackLogged(AttackId("a1"), 1, lyra, gob, hit = true, damage = 4),
                Event.TurnChanged(gob, 1),
                Event.TurnChanged(lyra, 2),
                Event.AttackEdited(AttackId("a1"), hit = true, damage = 5),
                Event.AttackEdited(AttackId("a1"), hit = false, damage = 0),
                Event.AttackDeleted(AttackId("a1")),
            ),
        )
        val a1 = t.lines.first { it.text.startsWith("Lyra → Goblin") }
        assertEquals(1, a1.round)
        assertEquals("Lyra → Goblin: hit, 4 damage (Goblin 3/7)", a1.text) // as it happened
        assertEquals(listOf("corrected in round 2: hit, 5 damage (Goblin 2/7)", "corrected in round 2: miss (Goblin 7/7)", "removed in round 2"), a1.notes)
        assertTrue(t.lines.none { it.text.startsWith("Correction") || it.text.startsWith("Entry removed") }) // no separate lines
        assertTrue(t.attacks.isEmpty()) // and gone from the final attack list
    }

    private fun show(l: TranscriptLine) =
        "${l.round}: ${if (l.turn) "*" else ""}${l.text}" + if (l.notes.isEmpty()) "" else l.notes.joinToString("; ", " [", "]")

    @Test
    fun `totals count damage as logged, and players only count what they can see`() {
        var s = events.fold(EncounterState(round = 0, order = emptyList(), currentId = null, combatants = emptyMap()), ::evolve)
        // The hidden spy stabs Lyra; only the DM's totals include it.
        s = evolve(s, Event.AttackLogged(AttackId("a4"), 2, spy, lyra, hit = true, damage = 3))
        fun totals(viewer: Actor, id: CombatantId) = viewFor(s, viewer).combatants.first { it.id == id }.totals
        assertEquals(Totals(dealt = 12, taken = 5, hits = 1, misses = 0, biggestHit = 12), totals(Actor.Dm, lyra).copy(taken = 5))
        assertEquals(8, totals(Actor.Dm, lyra).taken)
        assertEquals(5, totals(Actor.Player(PlayerId("p1")), lyra).taken)
        assertEquals(12, totals(Actor.Dm, gob).taken)
        assertEquals(Totals(dealt = 5, taken = 12, hits = 1, misses = 0, biggestHit = 5), totals(Actor.Player(PlayerId("p1")), gob))
        assertTrue(viewFor(s, Actor.Player(PlayerId("p1"))).combatants.none { it.id == spy })
    }
}
