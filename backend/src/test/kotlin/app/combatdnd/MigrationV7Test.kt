package app.combatdnd

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Upgrades a database that's at V6, with the kinds of data a real one can have, to V7. Runs in its own
 * schema so it doesn't touch the tables the other tests use. Needs Postgres (RUN_DB_TESTS=true).
 */
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class MigrationV7Test {
    private val url = "jdbc:postgresql://localhost:5432/combat_dnd"
    private val schema = "v7_upgrade_check"

    private fun flyway(target: String) = Flyway.configure()
        .dataSource(url, "combat", "combat")
        .schemas(schema)
        .target(target)
        .cleanDisabled(false)
        .load()

    private fun <T> db(block: (Connection) -> T): T = DriverManager.getConnection(url, "combat", "combat").use { c ->
        c.createStatement().execute("set search_path to $schema")
        block(c)
    }

    private fun Connection.run(sql: String) = createStatement().use { it.execute(sql) }

    private fun Connection.one(sql: String): String? = createStatement().use { s ->
        s.executeQuery(sql).use { rs -> if (rs.next()) rs.getString(1) else null }
    }

    @AfterEach
    fun dropSchema() {
        DriverManager.getConnection(url, "combat", "combat").use { it.run("drop schema if exists $schema cascade") }
    }

    @Test
    fun `V7 cleans up broken portrait links, links portraits to campaigns, and drops the old column`() {
        flyway("6").migrate()
        val camp = "11111111-1111-4111-8111-111111111111"
        val gone = "22222222-2222-4222-8222-222222222222" // a campaign that was deleted
        db { c ->
            c.run("insert into campaign (id, name, join_code, dm_token_hash) values ('$camp', 'Tuesday', 'ABC234', 'old')")
            c.run("insert into portrait (key, bytes) values ('$camp/a.jpg', '\\x01'), ('$gone/b.jpg', '\\x02')")
            // Lyra's picture exists; Bram's key points at nothing (a picture saved as a file before V3).
            c.run("insert into character (campaign_id, name, portrait_key, ac, max_hp, current_hp) values ('$camp', 'Lyra', '$camp/a.jpg', 15, 27, 27), ('$camp', 'Bram', '$camp/missing.jpg', 16, 31, 31)")
            c.run("insert into npc (campaign_id, name, portrait_key, ac, max_hp, current_hp) values ('$camp', 'Mira', '$camp/missing.jpg', 14, 24, 24)")
        }

        flyway("latest").migrate()

        db { c ->
            assertEquals("$camp/a.jpg", c.one("select portrait_key from character where name = 'Lyra'"))
            assertEquals(null, c.one("select portrait_key from character where name = 'Bram'"))
            assertEquals(null, c.one("select portrait_key from npc where name = 'Mira'"))
            // The orphaned picture is gone; the other now belongs to its campaign.
            assertEquals("1", c.one("select count(*) from portrait"))
            assertEquals(camp, c.one("select campaign_id from portrait"))
            assertEquals("0", c.one("select count(*) from information_schema.columns where table_schema = '$schema' and table_name = 'campaign' and column_name = 'dm_token_hash'"))
            assertTrue(c.one("select col_description('$schema.character'::regclass, 5)")!!.startsWith("A picture in the portrait table"))

            // From now on the database refuses a key that points at nothing...
            assertFailsWith<SQLException> { c.run("update character set portrait_key = 'nope.jpg' where name = 'Bram'") }
            // ...and deleting the campaign deletes its pictures too.
            c.run("delete from campaign where id = '$camp'")
            assertEquals("0", c.one("select count(*) from portrait"))
        }

        // The new indexes exist.
        db { c ->
            val names = listOf("encounter_campaign_idx", "monster_template_campaign_idx", "encounter_party_character_idx", "encounter_enemy_template_idx", "encounter_npc_npc_idx", "portrait_campaign_idx")
            names.forEach { assertEquals("1", c.one("select count(*) from pg_indexes where schemaname = '$schema' and indexname = '$it'"), it) }
        }
    }
}
