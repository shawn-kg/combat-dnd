package app.combatdnd.encounter.app

import app.combatdnd.encounter.domain.Actor
import app.combatdnd.encounter.domain.Command
import app.combatdnd.encounter.domain.Decision
import app.combatdnd.encounter.domain.EncounterState
import app.combatdnd.encounter.api.EncounterViewDto
import app.combatdnd.encounter.api.toDto
import app.combatdnd.encounter.domain.Reason
import app.combatdnd.encounter.domain.decide
import app.combatdnd.encounter.domain.evolve
import app.combatdnd.encounter.domain.viewFor
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class EncounterNotFound(id: UUID) : RuntimeException("No encounter $id.")
class StaleVersion : RuntimeException("The table changed. Check and try again.")
class CommandRejected(val reason: Reason, message: String) : RuntimeException(message)

/** The empty tracker every encounter's events are folded onto. */
val EMPTY_ENCOUNTER = EncounterState(round = 0, order = emptyList(), currentId = null, combatants = emptyMap())

/**
 * Runs commands against encounters. Each encounter's state is cached in memory and guarded by its own lock;
 * the event log in Postgres is the source of truth, so a restart (or undo) just replays it.
 * Assumes a single server instance (see docs/design.md, "Infrastructure").
 */
@Service
class EncounterService(
    private val repo: EncounterRepository,
    private val hub: EncounterHub,
    private val tx: TransactionTemplate,
) {
    private class Loaded(val name: String, val campaignId: UUID, var state: EncounterState, var revision: Long)

    private val cache = ConcurrentHashMap<UUID, Loaded>()
    private val locks = ConcurrentHashMap<UUID, ReentrantLock>()

    private fun <T> locked(id: UUID, block: () -> T): T = locks.computeIfAbsent(id) { ReentrantLock() }.withLock(block)

    private fun load(id: UUID): Loaded = cache[id] ?: run {
        if (!repo.exists(id)) throw EncounterNotFound(id)
        Loaded(repo.name(id), repo.campaignId(id), replay(id), repo.version(id)).also { cache[id] = it }
    }

    private fun replay(id: UUID) = repo.events(id).fold(EMPTY_ENCOUNTER, ::evolve)

    /** The client-facing version is the encounter's revision, not the internal event count. */
    private fun Loaded.snapshot() = state.copy(version = revision)

    fun view(id: UUID, actor: Actor): EncounterViewDto = locked(id) { load(id).let { viewFor(it.snapshot(), actor).toDto(it.name, it.campaignId) } }

    /** Applies one command and returns the new revision. Throws on a stale version or a rejected command. */
    fun handle(id: UUID, actor: Actor, command: Command, expectedVersion: Long?): Long {
        val (meta, snapshot) = locked(id) {
            val loaded = load(id)
            if (expectedVersion != null && expectedVersion != loaded.revision) throw StaleVersion()
            when (val d = decide(loaded.state, command, actor)) {
                is Decision.Rejected -> throw CommandRejected(d.reason, d.message)
                is Decision.Accepted -> {
                    val revision = loaded.revision + 1
                    tx.executeWithoutResult {
                        repo.append(id, batch = revision, actor = actor, events = d.events)
                        repo.setVersion(id, revision)
                    }
                    loaded.state = d.events.fold(loaded.state, ::evolve)
                    loaded.revision = revision
                    (loaded.name to loaded.campaignId) to loaded.snapshot()
                }
            }
        }
        hub.broadcast(id, snapshot, meta.first, meta.second)
        return snapshot.version
    }

    /** DM only: reverses the last command (all of its events) by replaying the log without it. */
    fun undo(id: UUID, actor: Actor): Long {
        if (actor != Actor.Dm) throw CommandRejected(Reason.FORBIDDEN, "Only the DM can undo.")
        val (meta, snapshot) = locked(id) {
            val loaded = load(id)
            val revision = loaded.revision + 1
            val undone = tx.execute {
                repo.undoLastBatch(id).also { if (it) repo.setVersion(id, revision) }
            } ?: false
            if (!undone) throw CommandRejected(Reason.INVALID, "There is nothing to undo.")
            loaded.state = replay(id)
            loaded.revision = revision
            (loaded.name to loaded.campaignId) to loaded.snapshot()
        }
        hub.broadcast(id, snapshot, meta.first, meta.second)
        return snapshot.version
    }

    /** Opens a live stream for this viewer, starting with their current view. */
    fun subscribe(id: UUID, actor: Actor) = hub.subscribe(id, actor, view(id, actor))
}
