package app.combatdnd.auth

import app.combatdnd.encounter.domain.Actor
import app.combatdnd.encounter.domain.PlayerId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

class NotSignedIn(message: String = "Join the campaign first.") : RuntimeException(message)

/** Works out who is making a request: the DM or a player. */
fun interface ActorResolver {
    fun resolve(request: HttpServletRequest): Actor
}

/**
 * Development only: `?as=dm` or `?as=player:<uuid>` (or the X-Dev-Actor header).
 * A query parameter because EventSource can't send headers. Real cookies arrive in milestone 3.
 */
@Component
@Profile("dev")
class DevActorResolver : ActorResolver {
    override fun resolve(request: HttpServletRequest): Actor {
        val raw = request.getParameter("as") ?: request.getHeader("X-Dev-Actor") ?: throw NotSignedIn("Add ?as=dm or ?as=player:<id>.")
        return when {
            raw == "dm" -> Actor.Dm
            raw.startsWith("player:") -> runCatching { UUID.fromString(raw.removePrefix("player:")) }
                .map { Actor.Player(PlayerId(it.toString())) }
                .getOrElse { throw NotSignedIn("That player id isn't a UUID.") }
            else -> throw NotSignedIn("Use ?as=dm or ?as=player:<id>.")
        }
    }
}

/** Outside development nobody is signed in until campaign joining exists (milestone 3). */
@Component
@Profile("!dev")
class NoActorResolver : ActorResolver {
    override fun resolve(request: HttpServletRequest): Actor = throw NotSignedIn()
}
