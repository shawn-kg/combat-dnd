package app.combatdnd.campaign

import java.net.URI

private val CHARACTER_PATH = Regex("""^(?:/profile/[^/]+)?/characters/(\d{1,12})(?:/.*)?$""")

/**
 * Turns a pasted D&D Beyond character link into its standard form, `https://www.dndbeyond.com/characters/<id>`,
 * or null if it isn't one. Only D&D Beyond character pages are accepted, so the tracker can't carry a link
 * that leads anywhere else.
 */
fun dndBeyondCharacterUrl(raw: String): String? {
    val text = raw.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
    val uri = runCatching { URI(text) }.getOrNull() ?: return null
    if (uri.scheme !in setOf("http", "https") || uri.rawUserInfo != null || uri.port != -1) return null
    if (uri.host?.lowercase() !in setOf("dndbeyond.com", "www.dndbeyond.com")) return null
    val id = CHARACTER_PATH.matchEntire(uri.path ?: "")?.groupValues?.get(1) ?: return null
    return "https://www.dndbeyond.com/characters/$id"
}
