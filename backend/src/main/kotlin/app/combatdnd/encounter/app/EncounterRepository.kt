package app.combatdnd.encounter.app

import app.combatdnd.encounter.domain.Actor
import app.combatdnd.encounter.domain.Event
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

/** The encounter row and its event log. Everything about a fight is in the events. */
@Repository
class EncounterRepository(private val jdbc: JdbcClient, private val codec: EventCodec) {

    fun exists(encounterId: UUID): Boolean =
        jdbc.sql("select count(*) from encounter where id = ?").param(encounterId).query(Long::class.java).single() > 0

    fun name(encounterId: UUID): String =
        jdbc.sql("select name from encounter where id = ?").param(encounterId).query(String::class.java).single()

    fun version(encounterId: UUID): Long =
        jdbc.sql("select version from encounter where id = ?").param(encounterId).query(Long::class.java).single()

    fun setVersion(encounterId: UUID, version: Long) {
        jdbc.sql("update encounter set version = ? where id = ?").params(version, encounterId).update()
    }

    /** Events that haven't been undone, in the order they happened. */
    fun events(encounterId: UUID): List<Event> =
        jdbc.sql("select type, payload::text as payload from encounter_event where encounter_id = ? and not undone order by seq")
            .param(encounterId)
            .query { rs, _ -> codec.decode(rs.getString("type"), rs.getString("payload")) }
            .list()

    fun append(encounterId: UUID, batch: Long, actor: Actor, events: List<Event>) {
        val next = jdbc.sql("select coalesce(max(seq), 0) + 1 from encounter_event where encounter_id = ?")
            .param(encounterId).query(Long::class.java).single()
        val (role, actorId) = when (actor) {
            Actor.Dm -> "DM" to null
            is Actor.Player -> "PLAYER" to UUID.fromString(actor.id.value)
        }
        events.forEachIndexed { i, event ->
            jdbc.sql(
                """
                insert into encounter_event (encounter_id, seq, batch, actor_role, actor_id, type, payload)
                values (?, ?, ?, ?, ?, ?, ?::jsonb)
                """.trimIndent(),
            ).params(listOf(encounterId, next + i, batch, role, actorId, codec.typeOf(event), codec.encode(event))).update()
        }
    }

    /** Marks the latest batch that isn't undone yet as undone. False when there is nothing left to undo. */
    fun undoLastBatch(encounterId: UUID): Boolean =
        jdbc.sql(
            """
            update encounter_event set undone = true
            where encounter_id = :id and batch = (
                select max(batch) from encounter_event where encounter_id = :id and not undone
            )
            """.trimIndent(),
        ).param("id", encounterId).update() > 0

    fun createEncounter(campaignId: UUID, name: String, status: String = "ACTIVE"): UUID =
        jdbc.sql("insert into encounter (campaign_id, name, status) values (?, ?, ?) returning id")
            .params(campaignId, name, status).query(UUID::class.java).single()
}
