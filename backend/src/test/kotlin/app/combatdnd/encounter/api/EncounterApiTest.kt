package app.combatdnd.encounter.api

import app.combatdnd.dev.DevSeed
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The tracker API over real HTTP and Postgres. Needs a database (see application.yml's DATABASE_URL),
 * so it only runs with RUN_DB_TESTS=true. CI provides a Postgres service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class EncounterApiTest(
    @Autowired private val env: Environment,
    @Autowired private val seed: DevSeed,
    @Autowired private val json: ObjectMapper,
) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val port get() = env.getProperty("local.server.port")
    private val dm = "dm"
    private val lyra = "player:${DevSeed.LYRA_PLAYER}"
    private val bram = "player:${DevSeed.BRAM_PLAYER}"

    private fun uri(path: String, actor: String?) =
        URI("http://localhost:$port$path" + (actor?.let { (if ('?' in path) "&" else "?") + "as=$it" } ?: ""))

    private fun get(path: String, actor: String?): HttpResponse<String> =
        http.send(HttpRequest.newBuilder(uri(path, actor)).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun post(path: String, actor: String?, body: String, csrfHeader: Boolean = true): HttpResponse<String> {
        val req = HttpRequest.newBuilder(uri(path, actor))
            .header("Content-Type", "application/json")
            .apply { if (csrfHeader) header("X-Requested-With", "combat-dnd") }
            .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        return http.send(req, HttpResponse.BodyHandlers.ofString())
    }

    private fun state(id: UUID, actor: String): JsonNode {
        val res = get("/api/encounters/$id/state", actor)
        assertEquals(200, res.statusCode(), res.body())
        return json.readTree(res.body())
    }

    private fun JsonNode.combatant(key: String): JsonNode? = get("combatants").firstOrNull { it.get("id").asString() == key }

    private fun command(id: UUID, actor: String, body: String) = post("/api/encounters/$id/commands", actor, body)

    @Test
    fun `players get a filtered tracker, the DM gets everything`() {
        val id = seed.newAmbush()
        val forLyra = state(id, lyra)
        val boss = assertNotNull(forLyra.combatant("boss"))
        assertTrue(boss.get("hp").isNull && boss.get("ac").isNull)
        assertNull(forLyra.combatant("archer"))
        assertEquals("lyra", forLyra.get("currentId").asString())
        assertTrue(forLyra.combatant("lyra")!!.get("controlledByViewer").asBoolean())

        val forDm = state(id, dm)
        assertEquals(22, forDm.combatant("boss")!!.get("hp").asInt())
        assertNotNull(forDm.combatant("archer"))
        assertEquals(2, forDm.combatant("boss")!!.get("lastTurn").get("attacks").size())
    }

    @Test
    fun `a player logs an attack on their turn and the DM can undo it`() {
        val id = seed.newAmbush()
        val res = command(id, lyra, """{"type":"LogAttack","attackerId":"lyra","targetId":"boss","hit":true,"damage":9}""")
        assertEquals(200, res.statusCode(), res.body())
        assertEquals(13, state(id, dm).combatant("boss")!!.get("hp").asInt())
        assertEquals(9, state(id, lyra).combatant("lyra")!!.get("lastTurn").get("damage").asInt())

        val undo = post("/api/encounters/$id/undo", dm, "")
        assertEquals(200, undo.statusCode(), undo.body())
        assertEquals(22, state(id, dm).combatant("boss")!!.get("hp").asInt())
        assertTrue(state(id, dm).combatant("lyra")!!.get("lastTurn").isNull)
    }

    @Test
    fun `permissions come back as 403 with a reason`() {
        val id = seed.newAmbush()
        val notYourTurn = command(id, bram, """{"type":"LogAttack","attackerId":"bram","targetId":"boss","hit":true,"damage":5}""")
        assertEquals(403, notYourTurn.statusCode())
        assertEquals("You can log attacks on your own turn.", json.readTree(notYourTurn.body()).get("message").asString())

        assertEquals(403, command(id, lyra, """{"type":"EditCombatant","id":"lyra","hp":27}""").statusCode())
        assertEquals(403, post("/api/encounters/$id/undo", lyra, "").statusCode())
    }

    @Test
    fun `a stale version gets 409 and nothing changes`() {
        val id = seed.newAmbush()
        val version = state(id, dm).get("version").asLong()
        assertEquals(200, command(id, dm, """{"type":"EditCombatant","id":"boss","ac":16,"expectedVersion":$version}""").statusCode())
        val stale = command(id, dm, """{"type":"EditCombatant","id":"boss","ac":17,"expectedVersion":$version}""")
        assertEquals(409, stale.statusCode())
        assertEquals(16, state(id, dm).combatant("boss")!!.get("ac").asInt())
    }

    @Test
    fun `requests without the header, a sign-in, or a real encounter are refused`() {
        val id = seed.newAmbush()
        assertEquals(403, post("/api/encounters/$id/commands", dm, """{"type":"NextTurn"}""", csrfHeader = false).statusCode())
        assertEquals(401, get("/api/encounters/$id/state", null).statusCode())
        assertEquals(404, get("/api/encounters/${UUID.randomUUID()}/state", dm).statusCode())
        assertEquals(400, command(id, dm, """{"type":"Fireball"}""").statusCode())
    }

    @Test
    fun `the live stream sends the current tracker, then every change`() {
        val id = seed.newAmbush()
        val events = LinkedBlockingQueue<JsonNode>()
        val req = HttpRequest.newBuilder(uri("/api/encounters/$id/stream", lyra)).header("Accept", "text/event-stream").GET().build()
        val stream = http.sendAsync(req, HttpResponse.BodyHandlers.ofLines()).thenAccept { res ->
            res.body().forEach { line -> if (line.startsWith("data:")) events.put(json.readTree(line.removePrefix("data:"))) }
        }
        val first = assertNotNull(events.poll(5, TimeUnit.SECONDS), "no initial state")
        assertNull(first.combatant("boss")!!.get("hp").takeUnless { it.isNull }) // still filtered for the player

        command(id, dm, """{"type":"RevealCombatant","id":"archer"}""")
        val next = assertNotNull(events.poll(5, TimeUnit.SECONDS), "no update after a command")
        assertTrue(next.get("version").asLong() > first.get("version").asLong())
        assertNotNull(next.combatant("archer"))
        stream.cancel(true)
    }
}
