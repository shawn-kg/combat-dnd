package app.combatdnd.dev

import app.combatdnd.encounter.app.EncounterRepository
import app.combatdnd.encounter.app.EncounterService
import app.combatdnd.encounter.domain.Actor
import app.combatdnd.encounter.domain.Combatant
import app.combatdnd.encounter.domain.CombatantId
import app.combatdnd.encounter.domain.Command
import app.combatdnd.encounter.domain.Controller
import app.combatdnd.encounter.domain.PlayerId
import app.combatdnd.encounter.domain.Side
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Development only: a campaign and the goblin ambush from the mockup, mid-fight on Lyra's turn, so the tracker
 * works before campaigns and prep exist. Open /combat/<DEV_ENCOUNTER>?as=dm and ?as=player:<LYRA_PLAYER>.
 */
@Component
@Profile("dev")
class DevSeed(
    private val jdbc: JdbcClient,
    private val repo: EncounterRepository,
    private val service: EncounterService,
) : ApplicationRunner {
    companion object {
        val DEV_CAMPAIGN: UUID = UUID.fromString("00000000-0000-4000-8000-00000000c0de")
        val DEV_ENCOUNTER: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000a1")
        val LYRA_PLAYER: UUID = UUID.fromString("00000000-0000-4000-8000-00000000017a")
        val BRAM_PLAYER: UUID = UUID.fromString("00000000-0000-4000-8000-000000000b7a")
    }

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        jdbc.sql(
            """
            insert into campaign (id, name, join_code, dm_token_hash) values (?, 'Dev campaign', 'DEV001', 'dev')
            on conflict (id) do nothing
            """.trimIndent(),
        ).param(DEV_CAMPAIGN).update()
        if (!repo.exists(DEV_ENCOUNTER)) {
            jdbc.sql("insert into encounter (id, campaign_id, name, status) values (?, ?, 'Goblin Ambush', 'ACTIVE')")
                .params(DEV_ENCOUNTER, DEV_CAMPAIGN).update()
            seedAmbush(DEV_ENCOUNTER)
        }
        log.info(
            "Dev encounter ready: /combat/{}?as=dm  (player view: ?as=player:{})",
            DEV_ENCOUNTER, LYRA_PLAYER,
        )
    }

    /** A fresh copy of the ambush in a new encounter. Tests use this to start from a known table. */
    fun newAmbush(): UUID = repo.createEncounter(DEV_CAMPAIGN, "Goblin Ambush (test)").also(::seedAmbush)

    private fun seedAmbush(id: UUID) {
        fun party(key: String, name: String, player: UUID?, init: Int, ac: Int, hp: Int, max: Int) = Combatant(
            CombatantId(key), name, Side.PARTY, player?.let { Controller.Player(PlayerId(it.toString())) } ?: Controller.Dm,
            initiative = init, ac = ac, hp = hp, maxHp = max,
        )
        fun enemy(key: String, name: String, init: Int, ac: Int, hp: Int, hidden: Boolean = false) =
            Combatant(CombatantId(key), name, Side.ENEMY, Controller.Dm, initiative = init, ac = ac, hp = hp, maxHp = hp, hidden = hidden)

        val roster = listOf(
            enemy("boss", "Goblin Boss", 21, 15, 30),
            party("thorin", "Thorin", null, 18, 18, 34, 48),
            party("lyra", "Lyra", LYRA_PLAYER, 15, 15, 22, 27),
            enemy("gob1", "Goblin 1", 12, 13, 7),
            enemy("gob2", "Goblin 2", 12, 13, 7),
            enemy("archer", "Goblin Archer", 11, 14, 9, hidden = true),
            party("bram", "Bram", BRAM_PLAYER, 9, 16, 19, 31),
        )
        val dm = Actor.Dm
        roster.forEach { service.handle(id, dm, Command.AddCombatant(it), null) }
        fun turn() = service.handle(id, dm, Command.NextTurn, null)
        fun hit(a: String, t: String, d: Int) = service.handle(id, dm, Command.LogAttack(CombatantId(a), CombatantId(t), true, d), null)
        turn() // round 1: Goblin Boss
        hit("boss", "thorin", 6); hit("boss", "thorin", 7)
        turn() // Thorin
        hit("thorin", "boss", 8)
        turn() // Lyra's turn
    }
}
