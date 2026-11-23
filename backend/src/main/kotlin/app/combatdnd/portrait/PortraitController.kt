package app.combatdnd.portrait

import app.combatdnd.auth.Session
import app.combatdnd.web.BadRequest
import app.combatdnd.web.Forbidden
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Duration
import java.util.UUID

data class PortraitUploaded(val portraitKey: String)

@RestController
class PortraitController(private val portraits: PortraitService, private val session: Session) {

    /** DM only: upload a picture (multipart field "file"); returns the key to put on a character or template. */
    @PostMapping("/api/campaigns/{campaignId}/portraits")
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(@PathVariable campaignId: UUID, @RequestParam("file") file: MultipartFile?, req: HttpServletRequest): PortraitUploaded {
        if (!session.isDm(req, campaignId)) throw Forbidden("Only the DM can upload portraits.")
        if (file == null || file.isEmpty) throw BadRequest("Choose a picture to upload.")
        return PortraitUploaded(portraits.save(campaignId, file.bytes))
    }

    /**
     * Serves a portrait. Keys are random, so knowing one is the permission; they never change once written,
     * so browsers may cache them for a year.
     */
    @GetMapping("/api/portraits/{campaignId}/{file}")
    fun get(@PathVariable campaignId: String, @PathVariable file: String): ResponseEntity<ByteArray> {
        val bytes = portraits.load("$campaignId/$file") ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .body(bytes)
    }
}
