package app.combatdnd.encounter.prep

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An encounter from prep to the end of the fight, over real HTTP with each browser keeping its own cookies.
 * Needs Postgres, so it only runs with RUN_DB_TESTS=true (CI provides one).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class EncounterLifecycleApiTest(@Autowired private val env: Environment, @Autowired private val json: ObjectMapper) {

    inner class Browser {
        private val http = HttpClient.newBuilder().cookieHandler(CookieManager()).build()

        fun call(method: String, path: String, body: String? = null): HttpResponse<String> {
            val req = HttpRequest.newBuilder(URI("http://localhost:${env.getProperty("local.server.port")}$path"))
                .header("Content-Type", "application/json")
                .header("X-Requested-With", "combat-dnd")
                .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
                .build()
            return http.send(req, HttpResponse.BodyHandlers.ofString())
        }

        fun post(path: String, body: String? = null) = call("POST", path, body)
        fun read(path: String): JsonNode = call("GET", path).also { assertEquals(200, it.statusCode(), it.body()) }.let { json.readTree(it.body()) }
        fun ok(method: String, path: String, body: String? = null): JsonNode? =
            call(method, path, body).also { assertTrue(it.statusCode() in 200..299, "$method $path -> ${it.statusCode()} ${it.body()}") }
                .body().takeIf { it.isNotBlank() }?.let { s -> json.readTree(s) as JsonNode }
    }

    private inner class Table {
        val dm = Browser()
        val id: String = dm.ok("POST", "/api/campaigns", """{"name":"Tuesday Group"}""")!!.get("id").asString()
        val code: String = dm.read("/api/campaigns/$id").get("joinCode").asString()
        val lyra = dm.ok("POST", "/api/campaigns/$id/characters", """{"name":"Lyra","ac":15,"maxHp":27}""")!!.get("id").asString()
        val bram = dm.ok("POST", "/api/campaigns/$id/characters", """{"name":"Bram","ac":16,"maxHp":31,"hp":19}""")!!.get("id").asString()
        val goblin = dm.ok("POST", "/api/campaigns/$id/monster-templates", """{"name":"Goblin","ac":13,"maxHp":7}""")!!.get("id").asString()
        val boss = dm.ok("POST", "/api/campaigns/$id/monster-templates", """{"name":"Goblin Boss","ac":15,"maxHp":30}""")!!.get("id").asString()

        /** A player who joined, was let in, and chose Lyra. */
        val player = Browser().also { b ->
            val pid = b.ok("POST", "/api/join/$code", """{"name":"Sam"}""")!!.get("playerId").asString()
            dm.ok("POST", "/api/campaigns/$id/players/$pid/approve")
            b.ok("POST", "/api/campaigns/$id/characters/$lyra/claim")
        }
    }

    private fun JsonNode.combatant(name: String): JsonNode = get("combatants").first { it.get("name").asString() == name }

    @Test
    fun `prepare, start, roll initiative, fight, and end with HP carried over`() {
        val t = Table()
        val enc = t.dm.ok("POST", "/api/campaigns/${t.id}/encounters", """{"name":"Ambush"}""")!!.get("id").asString()

        // Players don't see encounters that are still being prepared.
        assertEquals(0, t.player.read("/api/campaigns/${t.id}").get("encounters").size())
        assertEquals("PREPARING", t.dm.read("/api/campaigns/${t.id}").get("encounters")[0].get("status").asString())

        // Bram isn't playing tonight; two goblins (one hidden) and the boss.
        t.dm.ok(
            "PUT", "/api/encounters/$enc/prep",
            """{"name":"Road ambush","party":[{"characterId":"${t.bram}","included":false}],
               "enemies":[{"templateId":"${t.goblin}","count":2},{"templateId":"${t.boss}","count":1,"hidden":true}]}""",
        )
        val prep = t.dm.read("/api/encounters/$enc/prep")
        assertEquals("Road ambush", prep.get("name").asString())
        assertEquals(listOf(true, false), listOf(t.lyra, t.bram).map { c -> prep.get("party").first { it.get("characterId").asString() == c }.get("included").asBoolean() })
        assertEquals(listOf(2, 1), prep.get("enemies").toList().map { it.get("count").asInt() })

        // Only the DM preps and starts.
        assertEquals(403, t.player.call("GET", "/api/encounters/$enc/prep").statusCode())
        assertEquals(403, t.player.post("/api/encounters/$enc/start").statusCode())
        assertEquals(400, t.dm.post("/api/encounters/$enc/commands", """{"type":"NextTurn"}""").statusCode())

        t.dm.ok("POST", "/api/encounters/$enc/start")
        val rolling = t.dm.read("/api/encounters/$enc/state")
        assertEquals("ROLLING_INITIATIVE", rolling.get("status").asString())
        assertEquals(setOf("Lyra", "Goblin 1", "Goblin 2", "Goblin Boss"), rolling.get("combatants").toList().map { it.get("name").asString() }.toSet())
        assertTrue(rolling.combatant("Goblin Boss").get("hidden").asBoolean())
        assertEquals(409, t.dm.call("PUT", "/api/encounters/$enc/prep", """{"name":"x"}""").statusCode())
        assertEquals(400, t.dm.post("/api/encounters/$enc/undo").statusCode(), "undo can't take the roster back off")

        // The player now sees the fight on the campaign page, and their own character on the tracker.
        assertEquals(enc, t.player.read("/api/campaigns/${t.id}").get("encounters")[0].get("id").asString())
        assertTrue(t.player.read("/api/encounters/$enc/state").combatant("Lyra").get("controlledByViewer").asBoolean())

        val ids = rolling.get("combatants").associate { it.get("name").asString() to it.get("id").asString() }
        // The player rolls for Lyra; it shows up on the DM's screen. They can't roll for anyone else.
        t.player.ok("POST", "/api/encounters/$enc/commands", """{"type":"RollInitiative","id":"${t.lyra}","initiative":17}""")
        assertEquals(17, t.dm.read("/api/encounters/$enc/state").combatant("Lyra").get("initiative").asInt())
        assertEquals(403, t.player.post("/api/encounters/$enc/commands", """{"type":"RollInitiative","id":"${ids["Goblin 1"]}","initiative":1}""").statusCode())
        val inits = mapOf("Goblin 1" to 12, "Goblin 2" to 20, "Goblin Boss" to 5)
        val body = inits.entries.joinToString(",", "{\"type\":\"SetInitiatives\",\"initiatives\":{", "}}") { "\"${ids[it.key]}\":${it.value}" }
        t.dm.ok("POST", "/api/encounters/$enc/commands", body)
        assertEquals(
            listOf("Goblin 2", "Lyra", "Goblin 1", "Goblin Boss"),
            t.dm.read("/api/encounters/$enc/state").get("combatants").toList().map { it.get("name").asString() },
        )

        t.dm.ok("POST", "/api/encounters/$enc/begin")
        val active = t.dm.read("/api/encounters/$enc/state")
        assertEquals("ACTIVE", active.get("status").asString())
        assertEquals(1, active.get("round").asInt())
        assertEquals(ids["Goblin 2"], active.get("currentId").asString())
        assertEquals(403, t.player.post("/api/encounters/$enc/commands", """{"type":"RollInitiative","id":"${t.lyra}","initiative":30}""").statusCode())

        // Goblin 2 hits Lyra for 5, then it's Lyra's turn.
        t.dm.ok("POST", "/api/encounters/$enc/commands", """{"type":"LogAttack","attackerId":"${ids["Goblin 2"]}","targetId":"${t.lyra}","hit":true,"damage":5}""")
        t.dm.ok("POST", "/api/encounters/$enc/commands", """{"type":"NextTurn"}""")
        t.player.ok("POST", "/api/encounters/$enc/commands", """{"type":"LogAttack","attackerId":"${t.lyra}","targetId":"${ids["Goblin 1"]}","hit":true,"damage":7}""")

        assertEquals(403, t.player.post("/api/encounters/$enc/end").statusCode())
        t.dm.ok("POST", "/api/encounters/$enc/end")
        assertEquals("ENDED", t.dm.read("/api/encounters/$enc/state").get("status").asString())
        assertEquals(400, t.dm.post("/api/encounters/$enc/commands", """{"type":"NextTurn"}""").statusCode())

        // Lyra keeps her wounds; Bram, who sat out, is untouched.
        val chars = t.dm.read("/api/campaigns/${t.id}").get("characters").associate { it.get("name").asString() to it.get("hp").asInt() }
        assertEquals(mapOf("Lyra" to 22, "Bram" to 19), chars)
        assertEquals(0, t.player.read("/api/campaigns/${t.id}").get("encounters").size())
    }

    @Test
    fun `a late arrival joins mid-fight, run by their player, and their HP is saved at the end`() {
        val t = Table()
        // Ana plays Bram, who wasn't in the roster when the fight started.
        val ana = Browser()
        val anaId = ana.ok("POST", "/api/join/${t.code}", """{"name":"Ana"}""")!!.get("playerId").asString()
        t.dm.ok("POST", "/api/campaigns/${t.id}/players/$anaId/approve")
        ana.ok("POST", "/api/campaigns/${t.id}/characters/${t.bram}/claim")

        val enc = t.dm.ok("POST", "/api/campaigns/${t.id}/encounters", """{"name":"Late"}""")!!.get("id").asString()
        t.dm.ok(
            "PUT", "/api/encounters/$enc/prep",
            """{"name":"Late","party":[{"characterId":"${t.bram}","included":false}],"enemies":[{"templateId":"${t.goblin}","count":1}]}""",
        )
        t.dm.ok("POST", "/api/encounters/$enc/start")
        val goblin = t.dm.read("/api/encounters/$enc/state").combatant("Goblin").get("id").asString()
        t.dm.ok("POST", "/api/encounters/$enc/commands", """{"type":"SetInitiatives","initiatives":{"${t.lyra}":15,"$goblin":10}}""")
        t.dm.ok("POST", "/api/encounters/$enc/begin")

        // Bram arrives with his saved HP (19/31) and initiative 12: between Lyra and the goblin.
        t.dm.ok(
            "POST", "/api/encounters/$enc/commands",
            """{"type":"AddCombatant","id":"${t.bram}","name":"Bram","side":"PARTY","playerId":"$anaId",
               "initiative":12,"ac":16,"hp":19,"maxHp":31}""",
        )
        val state = t.dm.read("/api/encounters/$enc/state")
        assertEquals(listOf("Lyra", "Bram", "Goblin"), state.get("combatants").toList().map { it.get("name").asString() })
        assertTrue(ana.read("/api/encounters/$enc/state").combatant("Bram").get("controlledByViewer").asBoolean())
        // Only the DM brings people in, and the same character can't be added twice.
        assertEquals(403, ana.post("/api/encounters/$enc/commands", """{"type":"AddCombatant","id":"x","name":"X","side":"PARTY","initiative":1,"ac":1,"hp":1,"maxHp":1}""").statusCode())
        assertEquals(400, t.dm.post("/api/encounters/$enc/commands", """{"type":"AddCombatant","id":"${t.bram}","name":"Bram","side":"PARTY","initiative":1,"ac":16,"hp":19,"maxHp":31}""").statusCode())

        // Lyra's turn ends, Bram's turn: Ana logs his attack. Then the goblin hits Bram for 4.
        t.dm.ok("POST", "/api/encounters/$enc/commands", """{"type":"NextTurn"}""")
        ana.ok("POST", "/api/encounters/$enc/commands", """{"type":"LogAttack","attackerId":"${t.bram}","targetId":"$goblin","hit":true,"damage":3}""")
        ana.ok("POST", "/api/encounters/$enc/commands", """{"type":"NextTurn"}""")
        t.dm.ok("POST", "/api/encounters/$enc/commands", """{"type":"LogAttack","attackerId":"$goblin","targetId":"${t.bram}","hit":true,"damage":4}""")

        t.dm.ok("POST", "/api/encounters/$enc/end")
        val bram = t.dm.read("/api/campaigns/${t.id}").get("characters").first { it.get("name").asString() == "Bram" }
        assertEquals(15, bram.get("hp").asInt())
    }

    @Test
    fun `the DM deletes an encounter`() {
        val t = Table()
        val enc = t.dm.ok("POST", "/api/campaigns/${t.id}/encounters", """{"name":"Spare"}""")!!.get("id").asString()
        assertEquals(403, t.player.call("DELETE", "/api/encounters/$enc").statusCode())
        t.dm.ok("DELETE", "/api/encounters/$enc")
        assertEquals(404, t.dm.call("GET", "/api/encounters/$enc/prep").statusCode())
        assertEquals(0, t.dm.read("/api/campaigns/${t.id}").get("encounters").size())
    }
}
