package app.combatdnd.encounter.prep

import app.combatdnd.auth.ActorResolver
import app.combatdnd.campaign.CampaignRepository
import app.combatdnd.encounter.app.EncounterRepository
import app.combatdnd.encounter.app.EncounterService
import app.combatdnd.encounter.app.EncounterStatus
import app.combatdnd.encounter.domain.Actor
import app.combatdnd.encounter.domain.Combatant
import app.combatdnd.encounter.domain.CombatantId
import app.combatdnd.encounter.domain.Controller
import app.combatdnd.encounter.domain.PlayerId
import app.combatdnd.encounter.domain.Side
import app.combatdnd.encounter.domain.transcript
import app.combatdnd.web.BadRequest
import app.combatdnd.web.Conflict
import app.combatdnd.web.Forbidden
import app.combatdnd.web.NotFound
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * The DM's side of an encounter's life: create it, save who's in it ahead of the session,
 * start it (the roster goes onto the tracker), begin round 1, end it (party and NPC HP carries over), delete it.
 */
@Service
class PrepService(
    private val actors: ActorResolver,
    private val encounters: EncounterRepository,
    private val service: EncounterService,
    private val campaigns: CampaignRepository,
    private val prep: PrepRepository,
) {
    private fun dmOf(req: HttpServletRequest, encounterId: UUID): UUID {
        val meta = encounters.meta(encounterId) ?: throw NotFound("No such encounter.")
        if (actors.resolve(req, encounterId) != Actor.Dm) throw Forbidden("Only the DM can do that.")
        return meta.campaignId
    }

    private fun cleanName(name: String) = name.trim().ifEmpty { throw BadRequest("Give the encounter a name.") }.take(80)

    fun create(req: HttpServletRequest, campaignId: UUID, name: String): EncounterCreated {
        campaigns.campaign(campaignId) ?: throw NotFound("No such campaign.")
        if (actors.resolveForCampaign(req, campaignId) != Actor.Dm) throw Forbidden("Only the DM can do that.")
        return EncounterCreated(encounters.createEncounter(campaignId, cleanName(name), EncounterStatus.PREPARING))
    }

    fun view(req: HttpServletRequest, encounterId: UUID): PrepView {
        val campaignId = dmOf(req, encounterId)
        val meta = encounters.meta(encounterId)!!
        val included = prep.party(encounterId)
        val names = campaigns.players(campaignId).associate { it.id to it.name }
        val party = campaigns.characters(campaignId).map {
            PrepPartyMember(it.id, it.name, it.portraitKey, it.ac, it.hp, it.maxHp, it.playerId?.let(names::get), included[it.id] ?: true)
        }
        val templates = campaigns.templates(campaignId).associateBy { it.id }
        val enemies = prep.enemies(encounterId).mapNotNull { e ->
            templates[e.templateId]?.let { PrepEnemy(it.id, it.name, it.portraitKey, it.ac, it.maxHp, e.count, e.hidden) }
        }
        val inFight = prep.npcs(encounterId)
        val npcs = campaigns.npcs(campaignId).map {
            PrepNpc(it.id, it.name, it.portraitKey, it.ac, it.hp, it.maxHp, it.statsVisible, it.id in inFight, inFight[it.id] ?: false)
        }
        return PrepView(encounterId, campaignId, meta.name, meta.status, party, enemies, npcs)
    }

    @Transactional
    fun save(req: HttpServletRequest, encounterId: UUID, body: PrepRequest) {
        val campaignId = dmOf(req, encounterId)
        if (encounters.meta(encounterId)!!.status != EncounterStatus.PREPARING) throw Conflict("This fight has started, so its setup can't change.")
        val characters = campaigns.characters(campaignId).map { it.id }.toSet()
        val templates = campaigns.templates(campaignId).map { it.id }.toSet()
        if (body.party.any { it.characterId !in characters }) throw BadRequest("That character isn't in this campaign.")
        if (body.enemies.any { it.templateId !in templates }) throw BadRequest("That monster isn't in this campaign.")
        if (body.enemies.any { it.count !in 1..20 }) throw BadRequest("Between 1 and 20 of each monster.")
        val npcs = campaigns.npcs(campaignId).map { it.id }.toSet()
        if (body.npcs.any { it.npcId !in npcs }) throw BadRequest("That NPC isn't in this campaign.")
        prep.rename(encounterId, cleanName(body.name))
        prep.replace(encounterId, body.party.distinctBy { it.characterId }, body.enemies, body.npcs.distinctBy { it.npcId })
    }

    fun delete(req: HttpServletRequest, encounterId: UUID) {
        dmOf(req, encounterId)
        encounters.delete(encounterId)
        service.forget(encounterId)
    }

    /** Puts the saved roster on the tracker. Everyone starts at initiative 0 until the DM types them in. */
    fun start(req: HttpServletRequest, encounterId: UUID): Long {
        val campaignId = dmOf(req, encounterId)
        val included = prep.party(encounterId)
        val party = campaigns.characters(campaignId).filter { included[it.id] ?: true }.map {
            Combatant(
                id = CombatantId(it.id.toString()),
                name = it.name,
                side = Side.PARTY,
                controller = it.playerId?.let { p -> Controller.Player(PlayerId(p.toString())) } ?: Controller.Dm,
                initiative = 0,
                ac = it.ac,
                hp = it.hp,
                maxHp = it.maxHp,
                portraitKey = it.portraitKey,
            )
        }
        val templates = campaigns.templates(campaignId).associateBy { it.id }
        val rows = prep.enemies(encounterId).mapNotNull { e -> templates[e.templateId]?.let { it to e } }
        val totals = rows.groupingBy { it.first.name }.fold(0) { n, (_, e) -> n + e.count }
        val numbered = mutableMapOf<String, Int>()
        val enemies = rows.flatMap { (t, e) ->
            List(e.count) {
                val name = if (totals.getValue(t.name) == 1) t.name else "${t.name} ${numbered.merge(t.name, 1, Int::plus)}"
                Combatant(
                    id = CombatantId(UUID.randomUUID().toString()),
                    name = name,
                    side = Side.ENEMY,
                    controller = Controller.Dm,
                    initiative = 0,
                    ac = t.ac,
                    hp = t.maxHp,
                    maxHp = t.maxHp,
                    portraitKey = t.portraitKey,
                    hidden = e.hidden,
                )
            }
        }
        // NPCs use their own id, so End combat can save their HP like a character's.
        val npcRows = campaigns.npcs(campaignId).associateBy { it.id }
        val npcs = prep.npcs(encounterId).mapNotNull { (id, hidden) ->
            npcRows[id]?.let {
                Combatant(
                    id = CombatantId(it.id.toString()),
                    name = it.name,
                    side = Side.NPC,
                    controller = Controller.Dm,
                    initiative = 0,
                    ac = it.ac,
                    hp = it.hp,
                    maxHp = it.maxHp,
                    portraitKey = it.portraitKey,
                    hidden = hidden,
                    statsVisible = it.statsVisible,
                )
            }
        }
        return service.start(encounterId, Actor.Dm, party + npcs + enemies)
    }

    data class Download(val filename: String, val contentType: String, val body: String)

    /** DM only: the fight so far (or the whole fight, once over), as Markdown or CSV. */
    fun transcript(req: HttpServletRequest, encounterId: UUID, format: String): Download {
        val campaignId = dmOf(req, encounterId)
        val meta = encounters.meta(encounterId)!!
        if (meta.status == EncounterStatus.PREPARING) throw Conflict("This fight hasn't started, so there's nothing to export yet.")
        val t = transcript(encounters.events(encounterId))
        val base = meta.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '-').ifEmpty { "fight" }.take(60)
        return when (format) {
            "csv" -> Download("$base-attacks.csv", "text/csv; charset=utf-8", TranscriptWriter.csv(t))
            "md" -> {
                val playedOn = encounters.firstEventAt(encounterId)?.atZone(java.time.ZoneOffset.UTC)?.toLocalDate()
                val info = TranscriptInfo(meta.name, campaigns.campaign(campaignId)!!.name, meta.status, playedOn)
                Download("$base-transcript.md", "text/markdown; charset=utf-8", TranscriptWriter.markdown(t, info))
            }
            else -> throw BadRequest("Ask for format=md or format=csv.")
        }
    }

    fun begin(req: HttpServletRequest, encounterId: UUID): Long {
        dmOf(req, encounterId)
        return service.begin(encounterId, Actor.Dm)
    }

    /** Ends the fight and saves each character's HP for next time. */
    fun end(req: HttpServletRequest, encounterId: UUID): Long {
        val campaignId = dmOf(req, encounterId)
        val characters = campaigns.characters(campaignId).map { it.id.toString() }.toSet()
        val npcs = campaigns.npcs(campaignId).map { it.id.toString() }.toSet()
        return service.end(encounterId, Actor.Dm) { state ->
            state.combatants.values.forEach {
                when {
                    it.side == Side.PARTY && it.id.value in characters -> prep.saveHp(campaignId, UUID.fromString(it.id.value), it.hp)
                    it.side == Side.NPC && it.id.value in npcs -> prep.saveNpcHp(campaignId, UUID.fromString(it.id.value), it.hp)
                }
            }
        }
    }
}
