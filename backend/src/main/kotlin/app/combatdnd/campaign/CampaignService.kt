package app.combatdnd.campaign

import app.combatdnd.auth.NotSignedIn
import app.combatdnd.auth.PlayerStatus
import app.combatdnd.auth.Session
import app.combatdnd.encounter.app.EncounterRepository
import app.combatdnd.portrait.PortraitService
import app.combatdnd.web.BadRequest
import app.combatdnd.web.Conflict
import app.combatdnd.web.Forbidden
import app.combatdnd.web.NotFound
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.UUID

@Service
class CampaignService(
    private val repo: CampaignRepository,
    private val session: Session,
    private val portraits: PortraitService,
    private val encounters: EncounterRepository,
) {
    companion object {
        /** No 0/O or 1/I/L, so a code read aloud or typed from a phone gets through. */
        private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private val random = SecureRandom()
    }

    // ---------- who's asking ----------

    private fun campaignOr404(id: UUID) = repo.campaign(id) ?: throw NotFound("No such campaign.")

    private fun requireDm(req: HttpServletRequest, campaignId: UUID) {
        campaignOr404(campaignId)
        if (!session.isDm(req, campaignId)) throw Forbidden("Only the DM can do that.")
    }

    private fun approvedPlayer(req: HttpServletRequest, campaignId: UUID): PlayerRow {
        val me = session.player(req, campaignId) ?: throw session.notInCampaign(req, campaignId)
        if (me.status != PlayerStatus.APPROVED) throw Forbidden("Waiting for the DM to let you in.")
        return PlayerRow(me.id, me.campaignId, me.name, me.status)
    }

    // ---------- campaigns ----------

    @Transactional
    fun create(req: HttpServletRequest, res: HttpServletResponse, name: String): CampaignCreated {
        val clean = name.trim().ifEmpty { throw BadRequest("Give the campaign a name.") }.take(80)
        val token = Session.newToken()
        val code = newJoinCode()
        val id = repo.create(clean, code, Session.hash(token))
        session.signInAsDm(req, res, id, token)
        return CampaignCreated(id, code)
    }

    fun view(req: HttpServletRequest, campaignId: UUID): CampaignView {
        val c = campaignOr404(campaignId)
        val players = repo.players(campaignId)
        val names = players.associate { it.id to it.name }
        val characters = repo.characters(campaignId)
        val toDto = { ch: CharacterRow -> CharacterDto(ch.id, ch.name, ch.portraitKey, ch.ac, ch.hp, ch.maxHp, ch.playerId, ch.playerId?.let(names::get)) }
        val claimed = characters.filter { it.playerId != null }.associate { it.playerId!! to it.id }
        val playerDto = { p: PlayerRow -> PlayerDto(p.id, p.name, p.status, claimed[p.id]) }

        if (session.isDm(req, campaignId)) {
            val templates = repo.templates(campaignId).map { TemplateDto(it.id, it.name, it.portraitKey, it.ac, it.maxHp) }
            return CampaignView(c.id, c.name, "DM", c.joinCode, players.map(playerDto), characters.map(toDto), me = null, templates = templates,
                encounters = encounters.campaignEncounters(campaignId))
        }
        val me = session.player(req, campaignId) ?: throw session.notInCampaign(req, campaignId)
        if (me.status == PlayerStatus.REMOVED) throw NotSignedIn("The DM removed you from this campaign. Ask for the link again.")
        val meDto = PlayerDto(me.id, me.name, me.status, claimed[me.id])
        val visibleCharacters = if (me.status == PlayerStatus.APPROVED) characters.map(toDto) else emptyList()
        val fights = if (me.status == PlayerStatus.APPROVED) encounters.campaignEncounters(campaignId).filter { it.status.running } else emptyList()
        return CampaignView(c.id, c.name, "PLAYER", null, listOf(meDto), visibleCharacters, meDto, encounters = fights)
    }

    /** Campaigns this browser belongs to, for the home page. */
    fun mine(req: HttpServletRequest): List<MyCampaign> =
        session.campaignIds(req).mapNotNull { (id, role) ->
            val c = repo.campaign(id) ?: return@mapNotNull null
            val valid = if (role == "DM") session.isDm(req, id) else session.player(req, id)?.status?.let { it != PlayerStatus.REMOVED } == true
            if (valid) MyCampaign(c.id, c.name, role) else null
        }.distinctBy { it.id to it.role }

    fun rotateJoinCode(req: HttpServletRequest, campaignId: UUID): JoinCode {
        requireDm(req, campaignId)
        return newJoinCode().also { repo.setJoinCode(campaignId, it) }.let(::JoinCode)
    }

    private fun newJoinCode(): String {
        repeat(20) {
            val code = (1..6).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
            if (!repo.codeTaken(code)) return code
        }
        throw IllegalStateException("Couldn't find a free join code.")
    }

    // ---------- joining ----------

    fun joinInfo(code: String): JoinInfo {
        val c = repo.campaignByCode(code.uppercase()) ?: throw NotFound("That join link doesn't work. Ask the DM for a new one.")
        val returning = repo.players(c.id).filter { it.status == PlayerStatus.APPROVED }.map { JoinPlayer(it.id, it.name) }
        return JoinInfo(c.id, c.name, returning)
    }

    @Transactional
    fun join(req: HttpServletRequest, res: HttpServletResponse, code: String, body: JoinRequest): Joined {
        val c = repo.campaignByCode(code.uppercase()) ?: throw NotFound("That join link doesn't work. Ask the DM for a new one.")
        val token = Session.newToken()
        val playerId = when {
            body.returningPlayerId != null -> {
                val p = repo.player(c.id, body.returningPlayerId)?.takeIf { it.status != PlayerStatus.REMOVED }
                    ?: throw NotFound("That player isn't in this campaign.")
                repo.resetPlayer(p.id, Session.hash(token))
                p.id
            }
            else -> {
                val name = body.name?.trim().orEmpty().ifEmpty { throw BadRequest("Type your name.") }.take(40)
                repo.createPlayer(c.id, name, Session.hash(token))
            }
        }
        session.signInAsPlayer(req, res, c.id, token)
        return Joined(c.id, playerId)
    }

    // ---------- the DM managing players ----------

    private fun playerOr404(campaignId: UUID, playerId: UUID) = repo.player(campaignId, playerId) ?: throw NotFound("No such player.")

    fun approve(req: HttpServletRequest, campaignId: UUID, playerId: UUID) {
        requireDm(req, campaignId)
        playerOr404(campaignId, playerId)
        repo.setStatus(playerId, PlayerStatus.APPROVED)
    }

    /** Deny a join request, or remove an approved player. Either way they lose access and any claimed character. */
    @Transactional
    fun remove(req: HttpServletRequest, campaignId: UUID, playerId: UUID) {
        requireDm(req, campaignId)
        playerOr404(campaignId, playerId)
        repo.removePlayer(playerId)
    }

    // ---------- characters ----------

    private fun characterOr404(campaignId: UUID, id: UUID) = repo.character(campaignId, id) ?: throw NotFound("No such character.")

    private fun checkPortrait(key: String?, campaignId: UUID) {
        if (key != null && !portraits.belongsTo(key, campaignId)) throw BadRequest("That portrait wasn't uploaded to this campaign.")
    }

    private fun validate(body: CharacterRequest, hp: Int, campaignId: UUID): String {
        checkPortrait(body.portraitKey, campaignId)
        val name = body.name.trim().ifEmpty { throw BadRequest("Give the character a name.") }.take(60)
        if (body.ac < 0) throw BadRequest("AC can't be negative.")
        if (body.maxHp <= 0) throw BadRequest("Max HP must be above 0.")
        if (hp !in 0..body.maxHp) throw BadRequest("HP must be between 0 and ${body.maxHp}.")
        return name
    }

    fun createCharacter(req: HttpServletRequest, campaignId: UUID, body: CharacterRequest): CharacterDto {
        requireDm(req, campaignId)
        val hp = body.hp ?: body.maxHp
        val name = validate(body, hp, campaignId)
        val id = repo.createCharacter(campaignId, name, body.portraitKey, body.ac, body.maxHp, hp)
        return CharacterDto(id, name, body.portraitKey, body.ac, hp, body.maxHp, null, null)
    }

    fun updateCharacter(req: HttpServletRequest, campaignId: UUID, id: UUID, body: CharacterRequest) {
        requireDm(req, campaignId)
        val existing = characterOr404(campaignId, id)
        val hp = body.hp ?: minOf(existing.hp, body.maxHp)
        repo.updateCharacter(id, validate(body, hp, campaignId), body.portraitKey, body.ac, body.maxHp, hp)
    }

    fun deleteCharacter(req: HttpServletRequest, campaignId: UUID, id: UUID) {
        requireDm(req, campaignId)
        characterOr404(campaignId, id)
        repo.deleteCharacter(id)
    }

    fun unassign(req: HttpServletRequest, campaignId: UUID, id: UUID) {
        requireDm(req, campaignId)
        characterOr404(campaignId, id)
        repo.unassign(id)
    }

    // ---------- monster templates (DM only) ----------

    private fun validTemplate(body: TemplateRequest, campaignId: UUID): String {
        checkPortrait(body.portraitKey, campaignId)
        if (body.ac < 0) throw BadRequest("AC can't be negative.")
        if (body.maxHp <= 0) throw BadRequest("HP must be above 0.")
        return body.name.trim().ifEmpty { throw BadRequest("Give the monster a name.") }.take(60)
    }

    fun createTemplate(req: HttpServletRequest, campaignId: UUID, body: TemplateRequest): TemplateDto {
        requireDm(req, campaignId)
        val name = validTemplate(body, campaignId)
        val id = repo.createTemplate(campaignId, name, body.portraitKey, body.ac, body.maxHp)
        return TemplateDto(id, name, body.portraitKey, body.ac, body.maxHp)
    }

    fun updateTemplate(req: HttpServletRequest, campaignId: UUID, id: UUID, body: TemplateRequest) {
        requireDm(req, campaignId)
        repo.template(campaignId, id) ?: throw NotFound("No such monster.")
        repo.updateTemplate(id, validTemplate(body, campaignId), body.portraitKey, body.ac, body.maxHp)
    }

    fun deleteTemplate(req: HttpServletRequest, campaignId: UUID, id: UUID) {
        requireDm(req, campaignId)
        repo.template(campaignId, id) ?: throw NotFound("No such monster.")
        repo.deleteTemplate(id)
    }

    /** A player picks their character, once. Only the DM can change it afterwards (unassign). */
    @Transactional
    fun claim(req: HttpServletRequest, campaignId: UUID, id: UUID) {
        val me = approvedPlayer(req, campaignId)
        characterOr404(campaignId, id)
        if (repo.characterOf(me.id) != null) throw Conflict("You've already chosen your character. Ask the DM if you need to change it.")
        if (!repo.claim(id, me.id)) throw Conflict("Someone else just chose that character. Pick another.")
    }

    /** The DM, or the player who owns the character, sets its current HP. */
    fun setHp(req: HttpServletRequest, campaignId: UUID, id: UUID, hp: Int) {
        val ch = characterOr404(campaignId, id)
        if (!session.isDm(req, campaignId)) {
            val me = approvedPlayer(req, campaignId)
            if (ch.playerId != me.id) throw Forbidden("You can only change your own character's HP.")
        }
        if (hp !in 0..ch.maxHp) throw BadRequest("HP must be between 0 and ${ch.maxHp}.")
        repo.setHp(id, hp)
    }
}
