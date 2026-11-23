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
 * start it (the roster goes onto the tracker), begin round 1, end it (party HP carries over), delete it.
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
        return PrepView(encounterId, campaignId, meta.name, meta.status, party, enemies)
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
        prep.rename(encounterId, cleanName(body.name))
        prep.replace(encounterId, body.party.distinctBy { it.characterId }, body.enemies)
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
        return service.start(encounterId, Actor.Dm, party + enemies)
    }

    fun begin(req: HttpServletRequest, encounterId: UUID): Long {
        dmOf(req, encounterId)
        return service.begin(encounterId, Actor.Dm)
    }

    /** Ends the fight and saves each character's HP for next time. */
    fun end(req: HttpServletRequest, encounterId: UUID): Long {
        val campaignId = dmOf(req, encounterId)
        val characters = campaigns.characters(campaignId).map { it.id.toString() }.toSet()
        return service.end(encounterId, Actor.Dm) { state ->
            state.combatants.values
                .filter { it.side == Side.PARTY && it.id.value in characters }
                .forEach { prep.saveHp(campaignId, UUID.fromString(it.id.value), it.hp) }
        }
    }
}
