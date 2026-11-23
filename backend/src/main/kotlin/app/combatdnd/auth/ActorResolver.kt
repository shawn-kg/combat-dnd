package app.combatdnd.auth

import app.combatdnd.encounter.app.EncounterNotFound
import app.combatdnd.encounter.domain.Actor
import app.combatdnd.encounter.domain.PlayerId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.util.UUID

/** Works out who is making a request about an encounter: the DM or an approved player of its campaign. */
@Component
class ActorResolver(
    private val session: Session,
    private val jdbc: JdbcClient,
    @Value("\${combat.dev-sign-in:false}") private val devSignIn: Boolean,
) {
    fun resolve(request: HttpServletRequest, encounterId: UUID): Actor {
        if (devSignIn) devActor(request)?.let { return it }
        val campaignId = jdbc.sql("select campaign_id from encounter where id = ?").param(encounterId)
            .query(UUID::class.java).optional().orElseThrow { EncounterNotFound(encounterId) }
        return session.actorFor(request, campaignId)
    }

    /**
     * Development only (combat.dev-sign-in, on in the dev profile): `?as=dm` or `?as=player:<uuid>`,
     * or the X-Dev-Actor header, so the seeded encounter works without joining a campaign.
     */
    private fun devActor(request: HttpServletRequest): Actor? {
        val raw = request.getParameter("as") ?: request.getHeader("X-Dev-Actor") ?: return null
        return when {
            raw == "dm" -> Actor.Dm
            raw.startsWith("player:") -> runCatching { UUID.fromString(raw.removePrefix("player:")) }
                .map { Actor.Player(PlayerId(it.toString())) }
                .getOrElse { throw NotSignedIn("That player id isn't a UUID.") }
            else -> throw NotSignedIn("Use ?as=dm or ?as=player:<id>.")
        }
    }
}
