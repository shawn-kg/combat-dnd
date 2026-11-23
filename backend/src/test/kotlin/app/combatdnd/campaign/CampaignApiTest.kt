package app.combatdnd.campaign

import app.combatdnd.encounter.app.EncounterRepository
import app.combatdnd.encounter.app.EncounterService
import app.combatdnd.encounter.domain.Actor
import app.combatdnd.encounter.domain.Combatant
import app.combatdnd.encounter.domain.CombatantId
import app.combatdnd.encounter.domain.Command
import app.combatdnd.encounter.domain.Controller
import app.combatdnd.encounter.domain.PlayerId
import app.combatdnd.encounter.domain.Side
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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Campaigns, joining and characters over real HTTP, with each "browser" keeping its own cookies.
 * Needs Postgres, so it only runs with RUN_DB_TESTS=true (CI provides one).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class CampaignApiTest(
    @Autowired private val env: Environment,
    @Autowired private val json: ObjectMapper,
    @Autowired private val encounters: EncounterRepository,
    @Autowired private val encounterService: EncounterService,
) {
    /** One browser: its own cookie jar. */
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

        fun get(path: String) = call("GET", path)
        fun post(path: String, body: String? = null) = call("POST", path, body)
        fun read(path: String): JsonNode = get(path).also { assertEquals(200, it.statusCode(), it.body()) }.let { json.readTree(it.body()) }
    }

    private fun HttpResponse<String>.json(): JsonNode = json.readTree(body())

    /** A DM with a fresh campaign and two characters. */
    private inner class Table {
        val dm = Browser()
        val created = dm.post("/api/campaigns", """{"name":"Tuesday Group"}""").also { assertEquals(201, it.statusCode(), it.body()) }.json()
        val id: String = created.get("id").asString()
        val code: String = created.get("joinCode").asString()
        val lyra = dm.post("/api/campaigns/$id/characters", """{"name":"Lyra","ac":15,"maxHp":27}""").json().get("id").asString()
        val bram = dm.post("/api/campaigns/$id/characters", """{"name":"Bram","ac":16,"maxHp":31,"hp":19}""").json().get("id").asString()

        /** A new browser that joins and is approved by the DM. */
        fun approvedPlayer(name: String): Pair<Browser, String> {
            val b = Browser()
            val playerId = b.post("/api/join/$code", """{"name":"$name"}""").json().get("playerId").asString()
            assertEquals(204, dm.post("/api/campaigns/$id/players/$playerId/approve").statusCode())
            return b to playerId
        }
    }

    @Test
    fun `join, wait for approval, then choose a character once`() {
        val t = Table()
        assertEquals("DM", t.dm.read("/api/campaigns/${t.id}").get("role").asString())
        assertEquals(t.code, t.dm.read("/api/campaigns/${t.id}").get("joinCode").asString())

        val sam = Browser()
        assertEquals("Tuesday Group", sam.read("/api/join/${t.code}").get("campaignName").asString())
        val samId = sam.post("/api/join/${t.code}", """{"name":"Sam"}""").json().get("playerId").asString()

        val waiting = sam.read("/api/campaigns/${t.id}")
        assertEquals("PENDING", waiting.get("me").get("status").asString())
        assertEquals(0, waiting.get("characters").size()) // nothing to see until approved
        assertTrue(waiting.get("joinCode").isNull)
        assertEquals(403, sam.post("/api/campaigns/${t.id}/characters/${t.lyra}/claim").statusCode())

        assertEquals(204, t.dm.post("/api/campaigns/${t.id}/players/$samId/approve").statusCode())
        assertEquals(2, sam.read("/api/campaigns/${t.id}").get("characters").size())
        assertEquals(204, sam.post("/api/campaigns/${t.id}/characters/${t.lyra}/claim").statusCode())
        assertEquals(409, sam.post("/api/campaigns/${t.id}/characters/${t.bram}/claim").statusCode()) // once only

        val (jordan, _) = t.approvedPlayer("Jordan")
        val taken = jordan.post("/api/campaigns/${t.id}/characters/${t.lyra}/claim")
        assertEquals(409, taken.statusCode())
        assertEquals(204, jordan.post("/api/campaigns/${t.id}/characters/${t.bram}/claim").statusCode())

        val dmView = t.dm.read("/api/campaigns/${t.id}")
        val lyra = dmView.get("characters").first { it.get("id").asString() == t.lyra }
        assertEquals("Sam", lyra.get("playerName").asString())
        assertEquals(t.lyra, sam.read("/api/campaigns/${t.id}").get("me").get("characterId").asString())
    }

    @Test
    fun `only the DM manages the campaign`() {
        val t = Table()
        val (sam, samId) = t.approvedPlayer("Sam")
        assertEquals(403, sam.post("/api/campaigns/${t.id}/players/$samId/approve").statusCode())
        assertEquals(403, sam.post("/api/campaigns/${t.id}/characters", """{"name":"X","ac":10,"maxHp":5}""").statusCode())
        assertEquals(403, sam.post("/api/campaigns/${t.id}/join-code/rotate").statusCode())
        assertEquals(401, Browser().get("/api/campaigns/${t.id}").statusCode())

        val newCode = t.dm.post("/api/campaigns/${t.id}/join-code/rotate").json().get("joinCode").asString()
        assertEquals(404, Browser().get("/api/join/${t.code}").statusCode()) // the old link stops working
        assertEquals(200, Browser().get("/api/join/$newCode").statusCode())
    }

    @Test
    fun `removed players lose access and their character`() {
        val t = Table()
        val (sam, samId) = t.approvedPlayer("Sam")
        sam.post("/api/campaigns/${t.id}/characters/${t.lyra}/claim")
        assertEquals(204, t.dm.post("/api/campaigns/${t.id}/players/$samId/remove").statusCode())
        val gone = sam.get("/api/campaigns/${t.id}")
        assertEquals(401, gone.statusCode())
        assertEquals("You're no longer in this campaign. Ask the DM for the join link to rejoin.", gone.json().get("message").asString())
        assertEquals("Join the campaign first.", Browser().get("/api/campaigns/${t.id}").json().get("message").asString())
        val lyra = t.dm.read("/api/campaigns/${t.id}").get("characters").first { it.get("id").asString() == t.lyra }
        assertTrue(lyra.get("playerId").isNull)
    }

    @Test
    fun `rejoining from a new browser needs the DM again, and the old browser is signed out`() {
        val t = Table()
        val (oldLaptop, samId) = t.approvedPlayer("Sam")
        oldLaptop.post("/api/campaigns/${t.id}/characters/${t.lyra}/claim")

        val newLaptop = Browser()
        assertTrue(newLaptop.read("/api/join/${t.code}").get("players").any { it.get("name").asString() == "Sam" })
        newLaptop.post("/api/join/${t.code}", """{"returningPlayerId":"$samId"}""")
        assertEquals("PENDING", newLaptop.read("/api/campaigns/${t.id}").get("me").get("status").asString())
        assertEquals(401, oldLaptop.get("/api/campaigns/${t.id}").statusCode())

        t.dm.post("/api/campaigns/${t.id}/players/$samId/approve")
        assertEquals(t.lyra, newLaptop.read("/api/campaigns/${t.id}").get("me").get("characterId").asString()) // character kept
    }

    @Test
    fun `players set their own HP, nobody else's`() {
        val t = Table()
        val (sam, _) = t.approvedPlayer("Sam")
        sam.post("/api/campaigns/${t.id}/characters/${t.lyra}/claim")
        assertEquals(204, sam.call("PATCH", "/api/campaigns/${t.id}/characters/${t.lyra}/hp", """{"hp":20}""").statusCode())
        assertEquals(400, sam.call("PATCH", "/api/campaigns/${t.id}/characters/${t.lyra}/hp", """{"hp":99}""").statusCode())
        assertEquals(403, sam.call("PATCH", "/api/campaigns/${t.id}/characters/${t.bram}/hp", """{"hp":1}""").statusCode())
        assertEquals(204, t.dm.call("PATCH", "/api/campaigns/${t.id}/characters/${t.bram}/hp", """{"hp":31}""").statusCode())
        val chars = t.dm.read("/api/campaigns/${t.id}").get("characters")
        assertEquals(20, chars.first { it.get("id").asString() == t.lyra }.get("hp").asInt())
        assertEquals(31, chars.first { it.get("id").asString() == t.bram }.get("hp").asInt())
    }

    @Test
    fun `the tracker signs people in by cookie`() {
        val t = Table()
        val (sam, samId) = t.approvedPlayer("Sam")
        val pending = Browser().also { it.post("/api/join/${t.code}", """{"name":"Riley"}""") }
        val encounter = encounters.createEncounter(UUID.fromString(t.id), "Cookie test")
        encounterService.handle(
            encounter, Actor.Dm,
            Command.AddCombatant(Combatant(CombatantId("lyra"), "Lyra", Side.PARTY, Controller.Player(PlayerId(samId)), 15, 15, 27, 27)),
            null,
        )

        val forSam = sam.read("/api/encounters/$encounter/state")
        assertTrue(forSam.get("combatants").first().get("controlledByViewer").asBoolean())
        assertTrue(!forSam.get("viewerIsDm").asBoolean())
        assertTrue(t.dm.read("/api/encounters/$encounter/state").get("viewerIsDm").asBoolean())
        assertEquals(403, pending.get("/api/encounters/$encounter/state").statusCode())
        assertEquals(401, Browser().get("/api/encounters/$encounter/state").statusCode())
    }

    @Test
    fun `the DM signs in on another device with their DM sign-in link`() {
        val t = Table()
        val view = t.dm.read("/api/campaigns/${t.id}")
        assertEquals(false, view.get("dmLinkMade").asBoolean())
        assertEquals(1, view.get("dmDevices").asInt())

        // Only the DM makes the link.
        val stranger = Browser()
        assertEquals(403, stranger.post("/api/campaigns/${t.id}/dm-link").statusCode())
        val code = t.dm.post("/api/campaigns/${t.id}/dm-link").json().get("code").asString()
        assertTrue(t.dm.read("/api/campaigns/${t.id}").get("dmLinkMade").asBoolean())

        // A second laptop opens it and is the DM, alongside the first.
        val laptop = Browser()
        assertEquals("Tuesday Group", laptop.read("/api/dm-sign-in/$code").get("campaignName").asString())
        assertEquals(t.id, laptop.post("/api/dm-sign-in/$code").json().get("campaignId").asString())
        assertEquals("DM", laptop.read("/api/campaigns/${t.id}").get("role").asString())
        assertEquals("DM", t.dm.read("/api/campaigns/${t.id}").get("role").asString())
        assertEquals(2, t.dm.read("/api/campaigns/${t.id}").get("dmDevices").asInt())
        assertTrue(laptop.read("/api/me/campaigns").any { it.get("id").asString() == t.id && it.get("role").asString() == "DM" })
        // Opening it again on the same browser doesn't add another device.
        laptop.post("/api/dm-sign-in/$code")
        assertEquals(2, t.dm.read("/api/campaigns/${t.id}").get("dmDevices").asInt())

        // A new link replaces the old one; devices already signed in stay in.
        val newCode = t.dm.post("/api/campaigns/${t.id}/dm-link").json().get("code").asString()
        assertEquals(404, Browser().get("/api/dm-sign-in/$code").statusCode())
        assertEquals(404, Browser().post("/api/dm-sign-in/$code").statusCode())
        assertEquals("DM", laptop.read("/api/campaigns/${t.id}").get("role").asString())

        // The laptop signs out every other device: the first browser loses DM access.
        assertEquals(1, laptop.post("/api/campaigns/${t.id}/dm-devices/sign-out-others").json().get("signedOut").asInt())
        assertEquals(401, t.dm.get("/api/campaigns/${t.id}").statusCode())
        assertEquals("DM", laptop.read("/api/campaigns/${t.id}").get("role").asString())
        // ...and gets back in with the current link.
        t.dm.post("/api/dm-sign-in/$newCode")
        assertEquals("DM", t.dm.read("/api/campaigns/${t.id}").get("role").asString())
    }

    @Test
    fun `handing the campaign to another DM, then joining it as a player`() {
        val t = Table()
        // The person who set it up hands it over: the real DM signs in with the link and signs the others out.
        val code = t.dm.post("/api/campaigns/${t.id}/dm-link").json().get("code").asString()
        val realDm = Browser()
        realDm.post("/api/dm-sign-in/$code")
        assertEquals(1, realDm.post("/api/campaigns/${t.id}/dm-devices/sign-out-others").json().get("signedOut").asInt())
        realDm.post("/api/campaigns/${t.id}/dm-link") // and makes a fresh link only they have

        // The old laptop is no longer the DM, and joins like any player.
        assertEquals(401, t.dm.get("/api/campaigns/${t.id}").statusCode())
        val me = t.dm.post("/api/join/${t.code}", """{"name":"Shawn"}""").json().get("playerId").asString()
        assertEquals(204, realDm.post("/api/campaigns/${t.id}/players/$me/approve").statusCode())
        val view = t.dm.read("/api/campaigns/${t.id}")
        assertEquals("PLAYER", view.get("role").asString())
        assertTrue(t.dm.read("/api/me/campaigns").none { it.get("role").asString() == "DM" })
        assertTrue(t.dm.read("/api/me/campaigns").any { it.get("id").asString() == t.id && it.get("role").asString() == "PLAYER" })
    }

    @Test
    fun `the home page lists this browser's campaigns`() {
        val t = Table()
        val (sam, _) = t.approvedPlayer("Sam")
        assertEquals("DM", t.dm.read("/api/me/campaigns").first { it.get("id").asString() == t.id }.get("role").asString())
        assertEquals("PLAYER", sam.read("/api/me/campaigns").single().get("role").asString())
        assertEquals(0, Browser().read("/api/me/campaigns").size())
        assertNull(sam.read("/api/campaigns/${t.id}").get("joinCode").takeUnless { it.isNull })
    }
}
