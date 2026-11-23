package app.combatdnd.campaign

import app.combatdnd.web.TooManyRequests
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/api")
class CampaignController(private val service: CampaignService, private val limiter: JoinRateLimiter) {

    /** Start a campaign; this browser becomes its DM. */
    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody body: CreateCampaignRequest, req: HttpServletRequest, res: HttpServletResponse) = service.create(req, res, body.name)

    /** Campaigns this browser belongs to. */
    @GetMapping("/me/campaigns")
    fun mine(req: HttpServletRequest) = service.mine(req)

    @GetMapping("/campaigns/{id}")
    fun view(@PathVariable id: UUID, req: HttpServletRequest) = service.view(req, id)

    @PostMapping("/campaigns/{id}/join-code/rotate")
    fun rotate(@PathVariable id: UUID, req: HttpServletRequest) = service.rotateJoinCode(req, id)

    // ---------- joining ----------

    @GetMapping("/join/{code}")
    fun joinInfo(@PathVariable code: String, req: HttpServletRequest): JoinInfo {
        limiter.check(req)
        return service.joinInfo(code)
    }

    @PostMapping("/join/{code}")
    fun join(@PathVariable code: String, @RequestBody body: JoinRequest, req: HttpServletRequest, res: HttpServletResponse): Joined {
        limiter.check(req)
        return service.join(req, res, code, body)
    }

    // ---------- the DM managing players ----------

    @PostMapping("/campaigns/{id}/players/{playerId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun approve(@PathVariable id: UUID, @PathVariable playerId: UUID, req: HttpServletRequest) = service.approve(req, id, playerId)

    /** Deny a waiting player or remove an approved one. */
    @PostMapping("/campaigns/{id}/players/{playerId}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(@PathVariable id: UUID, @PathVariable playerId: UUID, req: HttpServletRequest) = service.remove(req, id, playerId)

    // ---------- characters ----------

    @PostMapping("/campaigns/{id}/characters")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCharacter(@PathVariable id: UUID, @RequestBody body: CharacterRequest, req: HttpServletRequest) =
        service.createCharacter(req, id, body)

    @PutMapping("/campaigns/{id}/characters/{characterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateCharacter(@PathVariable id: UUID, @PathVariable characterId: UUID, @RequestBody body: CharacterRequest, req: HttpServletRequest) =
        service.updateCharacter(req, id, characterId, body)

    @DeleteMapping("/campaigns/{id}/characters/{characterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCharacter(@PathVariable id: UUID, @PathVariable characterId: UUID, req: HttpServletRequest) =
        service.deleteCharacter(req, id, characterId)

    @PostMapping("/campaigns/{id}/characters/{characterId}/unassign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unassign(@PathVariable id: UUID, @PathVariable characterId: UUID, req: HttpServletRequest) = service.unassign(req, id, characterId)

    // ---------- monster templates ----------

    @PostMapping("/campaigns/{id}/monster-templates")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTemplate(@PathVariable id: UUID, @RequestBody body: TemplateRequest, req: HttpServletRequest) = service.createTemplate(req, id, body)

    @PutMapping("/campaigns/{id}/monster-templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateTemplate(@PathVariable id: UUID, @PathVariable templateId: UUID, @RequestBody body: TemplateRequest, req: HttpServletRequest) =
        service.updateTemplate(req, id, templateId, body)

    @DeleteMapping("/campaigns/{id}/monster-templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTemplate(@PathVariable id: UUID, @PathVariable templateId: UUID, req: HttpServletRequest) = service.deleteTemplate(req, id, templateId)

    @PostMapping("/campaigns/{id}/characters/{characterId}/claim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun claim(@PathVariable id: UUID, @PathVariable characterId: UUID, req: HttpServletRequest) = service.claim(req, id, characterId)

    @PatchMapping("/campaigns/{id}/characters/{characterId}/hp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setHp(@PathVariable id: UUID, @PathVariable characterId: UUID, @RequestBody body: HpRequest, req: HttpServletRequest) =
        service.setHp(req, id, characterId, body.hp)
}

/**
 * Join links are the only thing protecting a campaign (besides DM approval), so guessing codes is throttled:
 * at most 30 join requests per address per 10 minutes. In memory, which is fine for one server.
 */
@org.springframework.stereotype.Component
class JoinRateLimiter {
    private val windowMillis = 10 * 60 * 1000L
    private val limit = 30
    private val hits = ConcurrentHashMap<String, MutableList<Long>>()

    fun check(req: HttpServletRequest) {
        // Behind the host's proxy, the last X-Forwarded-For entry is the one the proxy added (the real client).
        // Earlier entries come from the client and could be made up to dodge the limit.
        val key = req.getHeader("X-Forwarded-For")?.substringAfterLast(',')?.trim()?.ifEmpty { null } ?: req.remoteAddr
        val now = System.currentTimeMillis()
        val list = hits.computeIfAbsent(key) { mutableListOf() }
        val allowed = synchronized(list) {
            list.removeAll { now - it > windowMillis }
            if (list.size >= limit) false else list.add(now)
        }
        if (!allowed) throw TooManyRequests("Too many tries. Wait a few minutes and try again.")
    }
}
