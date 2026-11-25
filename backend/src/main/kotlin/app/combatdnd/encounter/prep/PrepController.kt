package app.combatdnd.encounter.prep

import app.combatdnd.encounter.api.VersionResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** DM only: setting up encounters and moving them from prep to initiative to the fight to over. */
@RestController
@RequestMapping("/api")
class PrepController(private val service: PrepService) {

    @PostMapping("/campaigns/{campaignId}/encounters")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable campaignId: UUID, @RequestBody body: CreateEncounterRequest, req: HttpServletRequest) =
        service.create(req, campaignId, body.name)

    @GetMapping("/encounters/{id}/prep")
    fun view(@PathVariable id: UUID, req: HttpServletRequest) = service.view(req, id)

    /** Only while the encounter is PREPARING. */
    @PutMapping("/encounters/{id}/prep")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun save(@PathVariable id: UUID, @RequestBody body: PrepRequest, req: HttpServletRequest) = service.save(req, id, body)

    @DeleteMapping("/encounters/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID, req: HttpServletRequest) = service.delete(req, id)

    /** PREPARING -> ROLLING_INITIATIVE: the saved roster goes onto the tracker. */
    @PostMapping("/encounters/{id}/start")
    fun start(@PathVariable id: UUID, req: HttpServletRequest) = VersionResponse(service.start(req, id))

    /** ROLLING_INITIATIVE -> ACTIVE: round 1, the top of the order goes first. */
    @PostMapping("/encounters/{id}/begin")
    fun begin(@PathVariable id: UUID, req: HttpServletRequest) = VersionResponse(service.begin(req, id))

    /** DM only: download the fight's transcript, `format=md` (readable) or `format=csv` (one row per attack). */
    @GetMapping("/encounters/{id}/transcript")
    fun transcript(@PathVariable id: UUID, @RequestParam(defaultValue = "md") format: String, req: HttpServletRequest): ResponseEntity<String> {
        val d = service.transcript(req, id, format)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.filename).build().toString())
            .header(HttpHeaders.CONTENT_TYPE, d.contentType)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(d.body)
    }

    /** -> ENDED, saving the party's HP to their characters. */
    @PostMapping("/encounters/{id}/end")
    fun end(@PathVariable id: UUID, req: HttpServletRequest) = VersionResponse(service.end(req, id))
}
