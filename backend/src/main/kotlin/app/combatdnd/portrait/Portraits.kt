package app.combatdnd.portrait

import app.combatdnd.web.BadRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** Where portrait files live. Local disk for now; Cloudflare R2 (S3-compatible) arrives with deployment. */
interface PortraitStore {
    fun save(key: String, bytes: ByteArray)
    fun load(key: String): ByteArray?
}

@Component
class LocalPortraitStore(@Value("\${combat.portraits.dir:./data/portraits}") dir: String) : PortraitStore {
    private val root: Path = Path.of(dir).toAbsolutePath().normalize()

    private fun resolve(key: String): Path {
        val path = root.resolve(key).normalize()
        require(path.startsWith(root)) { "Bad portrait key" } // no ../ escapes
        return path
    }

    override fun save(key: String, bytes: ByteArray) {
        val path = resolve(key)
        Files.createDirectories(path.parent)
        Files.write(path, bytes)
    }

    override fun load(key: String): ByteArray? = runCatching { resolve(key) }.getOrNull()?.takeIf(Files::isRegularFile)?.let(Files::readAllBytes)
}

/**
 * Turns an upload into a portrait: decoded (JPEG or PNG), center-cropped to a square, resized to 256 × 256
 * and stored as a JPEG of a few KB. Keys are `<campaign>/<random>.jpg`, so they can't be guessed or collide.
 */
@Component
class PortraitService(private val store: PortraitStore) {
    companion object {
        const val SIZE = 256
        const val MAX_BYTES = 5 * 1024 * 1024
        private val KEY = Regex("""^[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg$""")

        fun isValidKey(key: String) = KEY.matches(key)
    }

    fun save(campaignId: UUID, bytes: ByteArray): String {
        if (bytes.size > MAX_BYTES) throw BadRequest("That picture is over 5 MB. Try a smaller one.")
        val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            ?: throw BadRequest("That file isn't a picture we can read. Use a JPEG or PNG.")
        val key = "$campaignId/${UUID.randomUUID()}.jpg"
        store.save(key, toJpeg(squareThumbnail(image)))
        return key
    }

    fun load(key: String): ByteArray? = if (isValidKey(key)) store.load(key) else null

    /** Does this key point at a portrait uploaded to this campaign? */
    fun belongsTo(key: String, campaignId: UUID) = isValidKey(key) && key.startsWith("$campaignId/")

    private fun squareThumbnail(src: BufferedImage): BufferedImage {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val out = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.color = Color(0x1B3239) // behind any transparency, the party background color
            g.fillRect(0, 0, SIZE, SIZE)
            g.drawImage(src, 0, 0, SIZE, SIZE, x, y, x + side, y + side, null)
        } finally {
            g.dispose()
        }
        return out
    }

    private fun toJpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { ios ->
            writer.output = ios
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = 0.85f
            }
            writer.write(null, IIOImage(image, null, null), params)
        }
        writer.dispose()
        return out.toByteArray()
    }
}
