package app.combatdnd

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.net.URI
import java.net.URLDecoder

@SpringBootApplication
class CombatDndApplication

fun main(args: Array<String>) {
    databaseUrlSettings(System.getenv("DATABASE_URL"))?.forEach { (k, v) -> System.setProperty(k, v) }
    runApplication<CombatDndApplication>(*args)
}

/**
 * Hosted Postgres (Neon, Render, ...) hands out `postgresql://user:password@host/db?sslmode=require`, but the JDBC driver
 * wants `jdbc:postgresql://host/db?...` with the user and password separate. This turns the first into the second,
 * so DATABASE_URL can be pasted exactly as the provider shows it. A `jdbc:` URL (or none) is left alone.
 */
fun databaseUrlSettings(url: String?): Map<String, String>? {
    if (url == null || !(url.startsWith("postgres://") || url.startsWith("postgresql://"))) return null
    val uri = URI(url)
    val port = if (uri.port == -1) "" else ":${uri.port}"
    val query = uri.rawQuery?.let { "?$it" } ?: ""
    val settings = mutableMapOf("spring.datasource.url" to "jdbc:postgresql://${uri.host}$port${uri.rawPath}$query")
    uri.rawUserInfo?.split(":", limit = 2)?.let { parts ->
        settings["spring.datasource.username"] = URLDecoder.decode(parts[0], Charsets.UTF_8)
        if (parts.size == 2) settings["spring.datasource.password"] = URLDecoder.decode(parts[1], Charsets.UTF_8)
    }
    return settings
}
