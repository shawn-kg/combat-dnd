package app.combatdnd.encounter.api

import app.combatdnd.auth.ActorResolver
import app.combatdnd.encounter.app.EncounterService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@RestController
@RequestMapping("/api/encounters/{id}")
class EncounterController(private val service: EncounterService, private val actors: ActorResolver) {

    /** This viewer's filtered tracker. */
    @GetMapping("/state")
    fun state(@PathVariable id: UUID, request: HttpServletRequest): EncounterViewDto =
        service.view(id, actors.resolve(request)).toDto()

    /** Every tracker command. Send the version you last saw as `expectedVersion`; a stale one gets 409. */
    @PostMapping("/commands")
    fun command(@PathVariable id: UUID, @RequestBody body: CommandRequest, request: HttpServletRequest): VersionResponse =
        VersionResponse(service.handle(id, actors.resolve(request), body.toCommand(), body.expectedVersion))

    /** DM only: reverses the last command. */
    @PostMapping("/undo")
    fun undo(@PathVariable id: UUID, request: HttpServletRequest): VersionResponse =
        VersionResponse(service.undo(id, actors.resolve(request)))

    /** Server-Sent Events: a "state" event with this viewer's tracker now and after every change. */
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@PathVariable id: UUID, request: HttpServletRequest): SseEmitter =
        service.subscribe(id, actors.resolve(request))
}
