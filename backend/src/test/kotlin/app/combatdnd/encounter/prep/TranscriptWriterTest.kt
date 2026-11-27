package app.combatdnd.encounter.prep

import app.combatdnd.encounter.app.EncounterStatus
import app.combatdnd.encounter.domain.AttackId
import app.combatdnd.encounter.domain.Combatant
import app.combatdnd.encounter.domain.CombatantId
import app.combatdnd.encounter.domain.Controller
import app.combatdnd.encounter.domain.Event
import app.combatdnd.encounter.domain.Side
import app.combatdnd.encounter.domain.transcript
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranscriptWriterTest {
    private val a = CombatantId("a")
    private val b = CombatantId("b")
    private val t = transcript(
        listOf(
            Event.CombatantAdded(Combatant(a, "=HYPERLINK(\"x\")", Side.PARTY, Controller.Dm, 10, 12, 20, 20)),
            Event.CombatantAdded(Combatant(b, "Grim, the *Bold*", Side.ENEMY, Controller.Dm, 5, 12, 9, 9)),
            Event.TurnChanged(a, 1),
            Event.AttackLogged(AttackId("a1"), 1, a, b, hit = true, damage = 4),
        ),
    )

    @Test
    fun `a correction is written as a note right under the attack it changes`() {
        val fixed = transcript(
            listOf(
                Event.CombatantAdded(Combatant(a, "Lyra", Side.PARTY, Controller.Dm, 10, 12, 20, 20)),
                Event.CombatantAdded(Combatant(b, "Goblin", Side.ENEMY, Controller.Dm, 5, 12, 7, 7)),
                Event.TurnChanged(a, 1),
                Event.AttackLogged(AttackId("a1"), 1, a, b, hit = true, damage = 4),
                Event.TurnChanged(b, 1),
                Event.AttackLogged(AttackId("a2"), 1, b, a, hit = true, damage = 2),
                Event.AttackEdited(AttackId("a1"), hit = true, damage = 6),
            ),
        )
        val md = TranscriptWriter.markdown(fixed, TranscriptInfo("Ambush", "Tuesday", EncounterStatus.ACTIVE, null))
        assertTrue(
            md.contains(
                "- **Lyra’s turn**\n" +
                    "  - Lyra → Goblin: hit, 4 damage (Goblin 3/7)\n" +
                    "    - ↳ corrected in round 1: hit, 6 damage (Goblin 1/7)\n" +
                    "- **Goblin’s turn**\n" +
                    "  - Goblin → Lyra: hit, 2 damage (Lyra 18/20)\n",
            ),
            md,
        )
    }

    @Test
    fun `CSV cells are quoted and can't run as spreadsheet formulas`() {
        val lines = TranscriptWriter.csv(t).trim().lines()
        assertEquals("round,attacker,target,result,damage,target_hp_after,target_max_hp,corrected", lines[0])
        assertEquals("1,\"'=HYPERLINK(\"\"x\"\")\",\"Grim, the *Bold*\",hit,4,5,9,no", lines[1])
        assertEquals("'-5", TranscriptWriter.csvCell("-5"))
        assertEquals("plain", TranscriptWriter.csvCell("plain"))
    }

    @Test
    fun `Markdown has rounds, a summary, and names can't turn into formatting`() {
        val md = TranscriptWriter.markdown(t, TranscriptInfo("Road ambush", "Tuesday Group", EncounterStatus.ENDED, LocalDate.of(2026, 11, 25)))
        assertTrue(md.startsWith("# Road ambush: fight transcript\n\nTuesday Group · 2026-11-25 · 1 round · over\n"), md)
        assertTrue(md.contains("## Round 1\n\n- Initiative order: "), md) // not indented: it isn't part of a turn
        assertTrue(md.contains("- **=HYPERLINK(\"x\")’s turn**\n  - "), md)
        assertTrue(md.contains("  - =HYPERLINK(\"x\") → Grim, the \\*Bold\\*: hit, 4 damage (Grim, the \\*Bold\\* 5/9)"), md)
        assertTrue(md.contains("| Combatant | Side | Dealt | Taken | Hits | Misses | Biggest hit | HP at end |"), md)
        assertTrue(md.contains("Most damage dealt: **=HYPERLINK(\"x\")** (4)."), md)
    }
}
