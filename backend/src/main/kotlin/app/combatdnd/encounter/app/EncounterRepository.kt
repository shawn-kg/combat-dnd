package app.combatdnd.encounter.app

import app.combatdnd.encounter.domain.Actor
import app.combatdnd.encounter.domain.Event
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * PREPARING: the DM is setting up the roster. ROLLING_INITIATIVE: everyone is on the tracker, the DM types initiatives.
 * ACTIVE: turns are running. ENDED: over; HP has been written back to the characters.
 */
enum class EncounterStatus { PREPARING, ROLLING_INITIATIVE, ACTIVE, ENDED;
    val running get() = this == ROLLING_INITIATIVE || this == ACTIVE
}

data class EncounterMeta(val name: String, val campaignId: UUID, val status: EncounterStatus, val version: Long, val lockedBatch: Long)

data class EncounterSummary(val id: UUID, val name: String, val status: EncounterStatus)

/** The encounter row and its event log. Everything about a fight is in the events. */
@Repository
class EncounterRepository(private val jdbc: JdbcClient, private val codec: EventCodec) {

    /** Everything about the encounter row except its events; null if there is no such encounter. */
    fun meta(encounterId: UUID): EncounterMeta? =
        jdbc.sql("select name, campaign_id, status, version, locked_batch from encounter where id = ?").param(encounterId)
            .query { rs, _ ->
                EncounterMeta(
                    rs.getString("name"), rs.getObject("campaign_id", UUID::class.java),
                    EncounterStatus.valueOf(rs.getString("status")), rs.getLong("version"), rs.getLong("locked_batch"),
                )
            }.optional().orElse(null)

    fun setStatus(encounterId: UUID, status: EncounterStatus) {
        jdbc.sql("update encounter set status = ? where id = ?").params(status.name, encounterId).update()
    }

    fun lockBatches(encounterId: UUID, upTo: Long) {
        jdbc.sql("update encounter set locked_batch = ? where id = ?").params(upTo, encounterId).update()
    }

    fun exists(encounterId: UUID): Boolean =
        jdbc.sql("select count(*) from encounter where id = ?").param(encounterId).query(Long::class.java).single() > 0

    fun campaignId(encounterId: UUID): UUID =
        jdbc.sql("select campaign_id from encounter where id = ?").param(encounterId).query(UUID::class.java).single()

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

    /**
     * Marks the latest batch that isn't undone yet as undone, unless it is locked (at or below `locked_batch`).
     * False when there is nothing left to undo.
     */
    fun undoLastBatch(encounterId: UUID): Boolean =
        jdbc.sql(
            """
            update encounter_event set undone = true
            where encounter_id = :id and batch = (
                select max(batch) from encounter_event where encounter_id = :id and not undone
            ) and batch > (select locked_batch from encounter where id = :id)
            """.trimIndent(),
        ).param("id", encounterId).update() > 0

    fun createEncounter(campaignId: UUID, name: String, status: EncounterStatus = EncounterStatus.ACTIVE): UUID =
        jdbc.sql("insert into encounter (campaign_id, name, status) values (?, ?, ?) returning id")
            .params(campaignId, name, status.name).query(UUID::class.java).single()

    fun delete(encounterId: UUID) {
        jdbc.sql("delete from encounter where id = ?").param(encounterId).update()
    }

    fun campaignEncounters(campaignId: UUID): List<EncounterSummary> =
        jdbc.sql("select id, name, status from encounter where campaign_id = ? order by created_at desc").param(campaignId)
            .query { rs, _ -> EncounterSummary(rs.getObject("id", UUID::class.java), rs.getString("name"), EncounterStatus.valueOf(rs.getString("status"))) }
            .list()
}
