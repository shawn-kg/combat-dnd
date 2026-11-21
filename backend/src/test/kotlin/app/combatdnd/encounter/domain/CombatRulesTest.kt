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

/** The goblin ambush from the mockup, starting on Lyra's turn in round 3. */
private fun ambush(
    shieldMode: ReactionMode = ReactionMode.ASK,
    current: CombatantId = LYRA,
): EncounterState {
    val cs = listOf(
        Combatant(
            BOSS, "Goblin Boss", Side.ENEMY, Controller.Dm, initiative = 21, hp = 22, maxHp = 30, ac = 15,
            reactions = listOf(ReactionDef("Parry", acBonus = 2)), attacksPerAction = 2,
            conditions = listOf(Condition("Frightened")),
        ),
        Combatant(
            LYRA, "Lyra", Side.PARTY, Controller.Player(LYRA_PLAYER), initiative = 15, hp = 22, maxHp = 27, ac = 15,
            reactions = listOf(ReactionDef("Shield", acBonus = 5, mode = shieldMode)), attacksPerAction = 2,
        ),
        Combatant(
            GOB1, "Goblin 1", Side.ENEMY, Controller.Dm, initiative = 12, hp = 7, maxHp = 7, ac = 13,
            conditions = listOf(Condition("Prone"), Condition("Poisoned", private = true)),
        ),
        Combatant(ARCHER, "Goblin Archer", Side.ENEMY, Controller.Dm, initiative = 11, hp = 9, maxHp = 9, ac = 14, hidden = true),
        Combatant(
            BRAM, "Bram", Side.PARTY, Controller.Player(BRAM_PLAYER), initiative = 9, hp = 19, maxHp = 31, ac = 16,
            conditions = listOf(Condition("Poisoned")),
        ),
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

private fun attack(target: CombatantId, roll: Int, natural: Natural? = null, cost: Cost = Cost.ACTION) =
    Command.DeclareAttack(target, "Scimitar", cost = cost, roll = roll, natural = natural)

class AttackFlowTest {
    @Test
    fun `player attack goes declare, hit, damage, DM applies`() {
        val s = ambush().run(
            lyraActor to attack(BOSS, roll = 18),
            Actor.Dm to Command.ResolveAttack(hit = true),
            lyraActor to Command.SubmitDamage(9, "slashing"),
        )
        assertEquals(AttackStage.DAMAGE_SUBMITTED, s.pending?.stage)

        val applied = s.run(Actor.Dm to Command.ApplyDamage(10)) // DM adjusted it
        assertEquals(12, applied.combatant(BOSS).hp)
        assertNull(applied.pending)
    }

    @Test
    fun `a miss ends the attack`() {
        val s = ambush().run(lyraActor to attack(BOSS, roll = 8), Actor.Dm to Command.ResolveAttack(hit = false))
        assertNull(s.pending)
        assertEquals(22, s.combatant(BOSS).hp)
    }

    @Test
    fun `only the DM calls hits`() {
        val s = ambush().run(lyraActor to attack(BOSS, roll = 18))
        val d = decide(s, Command.ResolveAttack(hit = true), lyraActor)
        assertIs<Decision.Rejected>(d)
        assertEquals(Reason.FORBIDDEN, d.reason)
    }

    @Test
    fun `players cannot act on someone else's turn`() {
        val d = decide(ambush(), attack(BOSS, roll = 18), bramActor)
        assertIs<Decision.Rejected>(d)
    }

    @Test
    fun `players cannot target hidden enemies`() {
        val d = decide(ambush(), attack(ARCHER, roll = 18), lyraActor)
        assertIs<Decision.Rejected>(d)
    }

    @Test
    fun `send back returns the roll to the attacker, who resubmits`() {
        val s = ambush().run(
            lyraActor to attack(BOSS, roll = 18),
            Actor.Dm to Command.SendBack("Roll with disadvantage"),
        )
        assertEquals(AttackStage.RESUBMIT, s.pending?.stage)
        assertEquals("Roll with disadvantage", s.pending?.sendBackReason)

        val again = s.run(lyraActor to Command.Resubmit(11, RollMode.DISADVANTAGE))
        assertEquals(AttackStage.DECLARED, again.pending?.stage)
        assertEquals(11, again.pending?.roll)
        assertNull(again.pending?.sendBackReason)
    }

    @Test
    fun `DM can send damage back for a crit`() {
        val s = ambush().run(
            lyraActor to attack(BOSS, roll = 20, natural = Natural.TWENTY),
            Actor.Dm to Command.ResolveAttack(hit = true),
            lyraActor to Command.SubmitDamage(7, "slashing"),
            Actor.Dm to Command.SendBack("Critical hit: double the dice"),
        )
        assertEquals(AttackStage.AWAITING_DAMAGE, s.pending?.stage)
    }

    @Test
    fun `enemy Parry raises AC before the DM calls it`() {
        val s = ambush().run(lyraActor to attack(BOSS, roll = 16), Actor.Dm to Command.EnemyReact("Parry"))
        assertEquals(17, s.combatant(BOSS).effectiveAc)
        assertTrue(s.combatant(BOSS).resources.reactionUsed)
        assertEquals(AttackStage.DECLARED, s.pending?.stage) // the DM still decides
    }

    @Test
    fun `the DM's damage applies without a confirmation step`() {
        val s = ambush(current = GOB1).run(
            Actor.Dm to attack(LYRA, roll = 22),
            Actor.Dm to Command.ResolveAttack(hit = true),
            lyraActor to Command.SkipReaction,
            Actor.Dm to Command.SubmitDamage(5, "slashing"),
        )
        assertEquals(17, s.combatant(LYRA).hp)
        assertNull(s.pending)
    }
}

class ReactionTest {
    private fun goblinHitsLyra(shieldMode: ReactionMode, roll: Int, natural: Natural? = null) =
        ambush(shieldMode, current = GOB1).run(
            Actor.Dm to attack(LYRA, roll = roll, natural = natural),
            Actor.Dm to Command.ResolveAttack(hit = true),
        )

    @Test
    fun `the reaction window opens after the hit, not before`() {
        val declared = ambush(current = GOB1).run(Actor.Dm to attack(LYRA, roll = 17))
        assertEquals(AttackStage.DECLARED, declared.pending?.stage)

        val hit = declared.run(Actor.Dm to Command.ResolveAttack(hit = true))
        assertEquals(AttackStage.REACTION_WINDOW, hit.pending?.stage)
    }

    @Test
    fun `Shield turns a close hit into a miss`() {
        val s = goblinHitsLyra(ReactionMode.ASK, roll = 17).run(lyraActor to Command.React("Shield"))
        assertNull(s.pending)
        assertEquals(20, s.combatant(LYRA).effectiveAc)
    }

    @Test
    fun `Shield that is not enough still lets the hit through`() {
        val s = goblinHitsLyra(ReactionMode.ASK, roll = 23).run(lyraActor to Command.React("Shield"))
        assertEquals(AttackStage.AWAITING_DAMAGE, s.pending?.stage)
    }

    @Test
    fun `automatic Shield fires only when it changes the outcome`() {
        assertNull(goblinHitsLyra(ReactionMode.AUTO_IF_TURNS_MISS, roll = 17).pending)

        val tooHigh = goblinHitsLyra(ReactionMode.AUTO_IF_TURNS_MISS, roll = 23)
        assertEquals(AttackStage.AWAITING_DAMAGE, tooHigh.pending?.stage)
        assertTrue(!tooHigh.combatant(LYRA).resources.reactionUsed)
    }

    @Test
    fun `automatic Shield never fires on a natural 20`() {
        val s = goblinHitsLyra(ReactionMode.AUTO_IF_TURNS_MISS, roll = 20, natural = Natural.TWENTY)
        assertEquals(AttackStage.AWAITING_DAMAGE, s.pending?.stage)
    }

    @Test
    fun `Never ask skips the window`() {
        val s = goblinHitsLyra(ReactionMode.NEVER, roll = 17)
        assertEquals(AttackStage.AWAITING_DAMAGE, s.pending?.stage)
    }

    @Test
    fun `a used reaction skips the window`() {
        val s = ambush(current = GOB1).run(
            lyraActor to Command.UseReaction(LYRA, "Opportunity attack"),
            Actor.Dm to attack(LYRA, roll = 17),
            Actor.Dm to Command.ResolveAttack(hit = true),
        )
        assertEquals(AttackStage.AWAITING_DAMAGE, s.pending?.stage)
    }

    @Test
    fun `only the target's player can react`() {
        val s = goblinHitsLyra(ReactionMode.ASK, roll = 17)
        assertIs<Decision.Rejected>(decide(s, Command.React("Shield"), bramActor))
    }
}

class TrackingNotEnforcingTest {
    @Test
    fun `extra attacks are counted, never blocked`() {
        val s = ambush().run(
            lyraActor to attack(BOSS, roll = 8),
            Actor.Dm to Command.ResolveAttack(hit = false),
            lyraActor to attack(BOSS, roll = 9),
            Actor.Dm to Command.ResolveAttack(hit = false),
            lyraActor to attack(BOSS, roll = 10), // a third Action attack (Action Surge, Haste...) is still allowed
            Actor.Dm to Command.ResolveAttack(hit = false),
        )
        assertEquals(3, s.combatant(LYRA).resources.attacksThisTurn)
    }

    @Test
    fun `free and bonus attacks mark their own resource`() {
        val s = ambush().run(
            lyraActor to attack(BOSS, roll = 8, cost = Cost.FREE),
            Actor.Dm to Command.ResolveAttack(hit = false),
            lyraActor to attack(BOSS, roll = 8, cost = Cost.BONUS),
            Actor.Dm to Command.ResolveAttack(hit = false),
        )
        val r = s.combatant(LYRA).resources
        assertTrue(r.bonusUsed)
        assertTrue(!r.actionUsed)
    }

    @Test
    fun `Dash doubles movement`() {
        val s = ambush().run(lyraActor to Command.UseStandardAction("Dash", Cost.ACTION))
        assertEquals(60, s.combatant(LYRA).movementLimit)
    }
}

class TurnOrderTest {
    @Test
    fun `next turn skips hidden and downed enemies and wraps the round`() {
        val s = ambush(current = GOB1).run(Actor.Dm to Command.NextTurn)
        assertEquals(BRAM, s.currentId) // archer is hidden

        val wrapped = s.run(bramActor to Command.NextTurn)
        assertEquals(BOSS, wrapped.currentId)
        assertEquals(4, wrapped.round)
    }

    @Test
    fun `a new turn resets resources and temporary AC`() {
        val shielded = ambush(current = GOB1).run(
            Actor.Dm to attack(LYRA, roll = 17),
            Actor.Dm to Command.ResolveAttack(hit = true),
            lyraActor to Command.React("Shield"),
        )
        val bossTurn = ambush().copy(combatants = shielded.combatants, currentId = BRAM)
            .run(bramActor to Command.NextTurn, Actor.Dm to Command.NextTurn) // boss, then lyra
        assertEquals(LYRA, bossTurn.currentId)
        assertEquals(15, bossTurn.combatant(LYRA).effectiveAc)
        assertTrue(!bossTurn.combatant(LYRA).resources.reactionUsed)
    }

    @Test
    fun `a player cannot end the turn while an attack is pending`() {
        val s = ambush().run(lyraActor to attack(BOSS, roll = 18))
        assertIs<Decision.Rejected>(decide(s, Command.NextTurn, lyraActor))
        assertIs<Decision.Accepted>(decide(s, Command.NextTurn, Actor.Dm)) // the DM can always move on
    }
}

class VisibilityTest {
    @Test
    fun `players never receive enemy numbers, private conditions or hidden enemies`() {
        val view = viewFor(ambush(), lyraActor)
        val gob = view.combatants.single { it.id == GOB1 }
        assertNull(gob.hp)
        assertNull(gob.ac)
        assertNull(gob.resources)
        assertEquals(listOf("Prone"), gob.conditions.map { it.name })
        assertTrue(view.combatants.none { it.id == ARCHER })

        val bram = view.combatants.single { it.id == BRAM }
        assertEquals(19, bram.hp) // the party sees each other's numbers
        assertTrue(view.combatants.single { it.id == LYRA }.controlledByViewer)
    }

    @Test
    fun `the DM sees everything`() {
        val view = viewFor(ambush(), Actor.Dm)
        val gob = view.combatants.single { it.id == GOB1 }
        assertEquals(7, gob.hp)
        assertEquals(2, gob.conditions.size)
        assertNotNull(view.combatants.singleOrNull { it.id == ARCHER })
    }

    @Test
    fun `a player's pending attack does not reveal the enemy's AC`() {
        val s = ambush().run(lyraActor to attack(BOSS, roll = 18))
        assertNull(viewFor(s, lyraActor).pending?.targetAc)
        assertEquals(15, viewFor(s, Actor.Dm).pending?.targetAc)
    }

    @Test
    fun `an enemy knocked out gets a private Unconscious`() {
        val s = ambush().run(
            lyraActor to attack(GOB1, roll = 18),
            Actor.Dm to Command.ResolveAttack(hit = true),
            lyraActor to Command.SubmitDamage(9, "slashing"),
            Actor.Dm to Command.ApplyDamage(9),
        )
        val forPlayer = viewFor(s, lyraActor).combatants.single { it.id == GOB1 }
        assertTrue(forPlayer.conditions.none { it.name == "Unconscious" })
    }
}

class HintsTest {
    @Test
    fun `hints come from conditions the viewer can see`() {
        val s = ambush()
        val forLyra = hintsFor(s.combatant(LYRA), s.combatant(GOB1), lyraActor).map { it.text }
        assertEquals(listOf("Goblin 1 is Prone: advantage for melee within 5 ft, disadvantage for ranged"), forLyra)

        val forDm = hintsFor(s.combatant(GOB1), s.combatant(BRAM), Actor.Dm)
        assertTrue(forDm.any { it.private && it.text.startsWith("Goblin 1 is Poisoned") })
    }

    @Test
    fun `your own conditions are phrased to you`() {
        val s = ambush(current = BRAM)
        val hints = hintsFor(s.combatant(BRAM), s.combatant(BOSS), bramActor).map { it.text }
        assertEquals(listOf("You are Poisoned: disadvantage on attack rolls"), hints)
    }
}

class ReplayTest {
    @Test
    fun `folding the same events always gives the same state`() {
        val start = ambush()
        val commands = listOf(
            lyraActor to attack(BOSS, roll = 18),
            Actor.Dm to Command.ResolveAttack(hit = true),
            lyraActor to Command.SubmitDamage(9, "slashing"),
            Actor.Dm to Command.ApplyDamage(9),
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
