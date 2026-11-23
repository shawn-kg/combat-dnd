package app.combatdnd.portrait

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Portrait uploads and monster templates over real HTTP. Needs Postgres: runs with RUN_DB_TESTS=true. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["combat.portraits.dir=build/test-portraits"],
)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class PortraitAndTemplateApiTest(@Autowired private val env: Environment, @Autowired private val json: ObjectMapper) {

    inner class Browser {
        private val http = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        private fun uri(path: String) = URI("http://localhost:${env.getProperty("local.server.port")}$path")

        fun call(method: String, path: String, body: String? = null): HttpResponse<String> = http.send(
            HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json").header("X-Requested-With", "combat-dnd")
                .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        fun bytes(path: String): HttpResponse<ByteArray> =
            http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())

        /** A multipart/form-data upload, the way a browser sends a file input. */
        fun upload(path: String, fileBytes: ByteArray, filename: String = "pic.png"): HttpResponse<String> {
            val boundary = "----combatdnd" + System.nanoTime()
            val head = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\nContent-Type: application/octet-stream\r\n\r\n"
            val body = head.toByteArray() + fileBytes + "\r\n--$boundary--\r\n".toByteArray()
            return http.send(
                HttpRequest.newBuilder(uri(path)).header("Content-Type", "multipart/form-data; boundary=$boundary")
                    .header("X-Requested-With", "combat-dnd").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        }
    }

    private fun HttpResponse<String>.json(): JsonNode = json.readTree(body())

    private fun png(width: Int, height: Int): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color.RED
        g.fillRect(0, 0, width, height)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }

    private fun newCampaign(dm: Browser): String = dm.call("POST", "/api/campaigns", """{"name":"Portraits"}""").json().get("id").asString()

    @Test
    fun `the DM uploads a picture and gets a 256 by 256 portrait back`() {
        val dm = Browser()
        val id = newCampaign(dm)
        val res = dm.upload("/api/campaigns/$id/portraits", png(800, 500))
        assertEquals(201, res.statusCode(), res.body())
        val key = res.json().get("portraitKey").asString()
        assertTrue(key.startsWith("$id/") && key.endsWith(".jpg"))

        val served = Browser().bytes("/api/portraits/$key") // anyone with the key, e.g. players in the tracker
        assertEquals(200, served.statusCode())
        assertEquals("image/jpeg", served.headers().firstValue("Content-Type").orElse(""))
        assertTrue(served.headers().firstValue("Cache-Control").orElse("").contains("immutable"))
        val img = ImageIO.read(ByteArrayInputStream(served.body()))
        assertEquals(256, img.width)
        assertEquals(256, img.height)
        assertTrue(served.body().size < 20_000, "portraits should be small")

        // and it can go on a character
        val ch = dm.call("POST", "/api/campaigns/$id/characters", """{"name":"Lyra","ac":15,"maxHp":27,"portraitKey":"$key"}""")
        assertEquals(201, ch.statusCode(), ch.body())
    }

    @Test
    fun `uploads are checked`() {
        val dm = Browser()
        val id = newCampaign(dm)
        assertEquals(400, dm.upload("/api/campaigns/$id/portraits", "not a picture".toByteArray(), "notes.txt").statusCode())
        assertEquals(403, Browser().upload("/api/campaigns/$id/portraits", png(10, 10)).statusCode())
        assertEquals(404, Browser().bytes("/api/portraits/$id/00000000-0000-0000-0000-000000000000.jpg").statusCode())
        assertEquals(404, Browser().bytes("/api/portraits/..%2F..%2Fetc/passwd").statusCode().let { if (it == 400) 404 else it })

        // a portrait from another campaign can't be borrowed
        val otherDm = Browser()
        val other = newCampaign(otherDm)
        val foreign = otherDm.upload("/api/campaigns/$other/portraits", png(10, 10)).json().get("portraitKey").asString()
        assertEquals(400, dm.call("POST", "/api/campaigns/$id/characters", """{"name":"X","ac":10,"maxHp":5,"portraitKey":"$foreign"}""").statusCode())
    }

    @Test
    fun `monster templates are the DM's alone`() {
        val dm = Browser()
        val id = newCampaign(dm)
        val created = dm.call("POST", "/api/campaigns/$id/monster-templates", """{"name":"Goblin","ac":13,"maxHp":7}""")
        assertEquals(201, created.statusCode(), created.body())
        val tid = created.json().get("id").asString()
        assertEquals(204, dm.call("PUT", "/api/campaigns/$id/monster-templates/$tid", """{"name":"Goblin","ac":14,"maxHp":7}""").statusCode())
        val listed = dm.call("GET", "/api/campaigns/$id").json().get("templates")
        assertEquals(14, listed.single().get("ac").asInt())
        assertEquals(400, dm.call("POST", "/api/campaigns/$id/monster-templates", """{"name":"","ac":13,"maxHp":7}""").statusCode())

        // a player sees no templates and can't change them
        val code = dm.call("GET", "/api/campaigns/$id").json().get("joinCode").asString()
        val sam = Browser()
        val samId = sam.call("POST", "/api/join/$code", """{"name":"Sam"}""").json().get("playerId").asString()
        dm.call("POST", "/api/campaigns/$id/players/$samId/approve")
        assertEquals(0, sam.call("GET", "/api/campaigns/$id").json().get("templates").size())
        assertEquals(403, sam.call("POST", "/api/campaigns/$id/monster-templates", """{"name":"Dragon","ac":19,"maxHp":200}""").statusCode())

        assertEquals(204, dm.call("DELETE", "/api/campaigns/$id/monster-templates/$tid").statusCode())
        assertEquals(0, dm.call("GET", "/api/campaigns/$id").json().get("templates").size())
    }

    companion object {
        init {
            Files.createDirectories(java.nio.file.Path.of("build/test-portraits"))
        }
    }
}
