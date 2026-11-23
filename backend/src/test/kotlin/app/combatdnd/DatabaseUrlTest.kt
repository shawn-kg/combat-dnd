package app.combatdnd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatabaseUrlTest {
    @Test
    fun `a provider URL becomes a JDBC URL with the user and password separate`() {
        val s = databaseUrlSettings("postgresql://combat_owner:p%40ss:word@ep-cool-1.us-east-2.aws.neon.tech/combat?sslmode=require&channel_binding=require")!!
        assertEquals("jdbc:postgresql://ep-cool-1.us-east-2.aws.neon.tech/combat?sslmode=require&channel_binding=require", s["spring.datasource.url"])
        assertEquals("combat_owner", s["spring.datasource.username"])
        assertEquals("p@ss:word", s["spring.datasource.password"])
    }

    @Test
    fun `a port is kept, and JDBC URLs or none are left alone`() {
        assertEquals("jdbc:postgresql://db.example.com:6543/x", databaseUrlSettings("postgres://u:p@db.example.com:6543/x")!!["spring.datasource.url"])
        assertNull(databaseUrlSettings("jdbc:postgresql://localhost:5432/combat_dnd"))
        assertNull(databaseUrlSettings(null))
    }
}
