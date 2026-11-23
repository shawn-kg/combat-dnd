package app.combatdnd.auth

import app.combatdnd.encounter.domain.Actor
import app.combatdnd.encounter.domain.PlayerId
import app.combatdnd.web.Forbidden
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.UUID

class NotSignedIn(message: String = "Join the campaign first.") : RuntimeException(message)

enum class PlayerStatus { PENDING, APPROVED, REMOVED }

data class SignedInPlayer(val id: UUID, val campaignId: UUID, val name: String, val status: PlayerStatus)

/**
 * Who a browser is, per campaign. Each browser holds a random secret in an HttpOnly cookie
 * (`cdnd_dm_<campaign>` or `cdnd_pl_<campaign>`); the database stores only its SHA-256 hash.
 * No passwords: losing the cookie means rejoining with the link and the DM approving again.
 */
@Component
class Session(private val jdbc: JdbcClient) {
    companion object {
        private const val DM_PREFIX = "cdnd_dm_"
        private const val PLAYER_PREFIX = "cdnd_pl_"
        private val random = SecureRandom()

        fun newToken(): String = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

        private fun equalHashes(a: String?, b: String?) =
            a != null && b != null && MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
    }

    private fun cookie(request: HttpServletRequest, name: String): String? = request.cookies?.firstOrNull { it.name == name }?.value

    fun isDm(request: HttpServletRequest, campaignId: UUID): Boolean {
        val token = cookie(request, DM_PREFIX + campaignId) ?: return false
        val stored = jdbc.sql("select dm_token_hash from campaign where id = ?").param(campaignId)
            .query(String::class.java).optional().orElse(null)
        return equalHashes(stored, hash(token))
    }

    /** The player this browser signed in as for this campaign, whatever their status; null if none or the token is stale. */
    fun player(request: HttpServletRequest, campaignId: UUID): SignedInPlayer? {
        val token = cookie(request, PLAYER_PREFIX + campaignId) ?: return null
        return jdbc.sql("select id, campaign_id, display_name, status from player where campaign_id = ? and token_hash = ?")
            .params(campaignId, hash(token))
            .query { rs, _ ->
                SignedInPlayer(rs.getObject("id", UUID::class.java), rs.getObject("campaign_id", UUID::class.java), rs.getString("display_name"), PlayerStatus.valueOf(rs.getString("status")))
            }.optional().orElse(null)
    }

    /**
     * Why there's no player: this browser never joined, or it did and its sign-in no longer works
     * (the DM denied or removed it, or the player rejoined from another browser).
     */
    fun notInCampaign(request: HttpServletRequest, campaignId: UUID): NotSignedIn =
        if (cookie(request, PLAYER_PREFIX + campaignId) != null) NotSignedIn("You're no longer in this campaign. Ask the DM for the join link to rejoin.")
        else NotSignedIn()

    /** The DM, or an approved player. Pending players are told to wait; anyone else must join first. */
    fun actorFor(request: HttpServletRequest, campaignId: UUID): Actor {
        if (isDm(request, campaignId)) return Actor.Dm
        val p = player(request, campaignId) ?: throw notInCampaign(request, campaignId)
        return when (p.status) {
            PlayerStatus.APPROVED -> Actor.Player(PlayerId(p.id.toString()))
            PlayerStatus.PENDING -> throw Forbidden("Waiting for the DM to let you in.")
            PlayerStatus.REMOVED -> throw NotSignedIn("The DM removed you from this campaign. Ask for the link again.")
        }
    }

    /** Campaigns this browser belongs to, read from its cookies and checked against the database. */
    fun campaignIds(request: HttpServletRequest): List<Pair<UUID, String>> =
        request.cookies.orEmpty().mapNotNull { c ->
            val role = when {
                c.name.startsWith(DM_PREFIX) -> "DM"
                c.name.startsWith(PLAYER_PREFIX) -> "PLAYER"
                else -> return@mapNotNull null
            }
            runCatching { UUID.fromString(c.name.removePrefix(if (role == "DM") DM_PREFIX else PLAYER_PREFIX)) }.getOrNull()?.let { it to role }
        }

    fun signInAsDm(request: HttpServletRequest, response: HttpServletResponse, campaignId: UUID, token: String) =
        setCookie(request, response, DM_PREFIX + campaignId, token)

    fun signInAsPlayer(request: HttpServletRequest, response: HttpServletResponse, campaignId: UUID, token: String) =
        setCookie(request, response, PLAYER_PREFIX + campaignId, token)

    private fun setCookie(request: HttpServletRequest, response: HttpServletResponse, name: String, value: String) {
        val https = request.isSecure || request.getHeader("X-Forwarded-Proto") == "https"
        val cookie = ResponseCookie.from(name, value)
            .httpOnly(true).secure(https).sameSite("Lax").path("/").maxAge(Duration.ofDays(365))
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }
}
