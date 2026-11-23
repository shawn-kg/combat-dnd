package app.combatdnd.encounter.app

import app.combatdnd.encounter.domain.AttackId
import app.combatdnd.encounter.domain.Combatant
import app.combatdnd.encounter.domain.CombatantId
import app.combatdnd.encounter.domain.Controller
import app.combatdnd.encounter.domain.Event
import app.combatdnd.encounter.domain.PlayerId
import app.combatdnd.encounter.domain.Side
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals

class EventCodecTest {
    private val codec = EventCodec(JsonMapper.builder().addModule(KotlinModule.Builder().build()).build())

    @Test
    fun `every event type survives a round trip through JSON`() {
        val lyra = CombatantId("lyra")
        val events: List<Event> = listOf(
            Event.CombatantAdded(
                Combatant(lyra, "Lyra", Side.PARTY, Controller.Player(PlayerId("p1")), 15, 15, 22, 27, portraitKey = "img/l.webp"),
            ),
            Event.CombatantAdded(Combatant(CombatantId("boss"), "Goblin Boss", Side.ENEMY, Controller.Dm, 21, 15, 30, 30, hidden = true)),
            Event.AttackLogged(AttackId("a1"), 3, lyra, CombatantId("boss"), hit = true, damage = 9),
            Event.AttackEdited(AttackId("a1"), hit = false, damage = 0),
            Event.AttackDeleted(AttackId("a1")),
            Event.TurnChanged(lyra, 3),
            Event.CombatantRemoved(lyra),
            Event.CombatantRevealed(lyra),
            Event.DefeatedSet(lyra, true),
            Event.CombatantEdited(lyra, "Lyra the Bold", null, 16, null, 20, null),
            Event.Reordered(listOf(lyra, CombatantId("boss"))),
            Event.InitiativeRolled(lyra, 14),
            Event.InitiativesSet(mapOf(lyra to 15, CombatantId("boss") to 21)),
        )
        val covered = events.map { it::class }.toSet()
        assertEquals(Event::class.sealedSubclasses.toSet(), covered, "add new event types to this test")
        events.forEach { e -> assertEquals(e, codec.decode(codec.typeOf(e), codec.encode(e))) }
    }

    @Test
    fun `ids are stored as plain strings`() {
        val json = codec.encode(Event.TurnChanged(CombatantId("lyra"), 3))
        assertEquals("""{"currentId":"lyra","round":3}""", json)
    }
}
