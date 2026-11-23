package app.combatdnd.encounter.app

import app.combatdnd.encounter.api.EncounterViewDto
import app.combatdnd.encounter.api.toDto
import app.combatdnd.encounter.domain.Actor
import app.combatdnd.encounter.domain.Combatant
import app.combatdnd.encounter.domain.Command
import app.combatdnd.encounter.domain.Decision
import app.combatdnd.encounter.domain.EncounterState
import app.combatdnd.encounter.domain.Event
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

/** What the hub needs to turn a state into each viewer's view. */
data class Header(val name: String, val campaignId: UUID, val status: EncounterStatus)

/**
 * Runs commands against encounters. Each encounter's state is cached in memory and guarded by its own lock;
 * the event log in Postgres is the source of truth, so a restart (or undo) just replays it.
 * Assumes a single server instance (see docs/design.md, "Infrastructure").
 *
 * Also moves an encounter through its life: start (roster onto the tracker), begin (round 1), end.
 */
@Service
class EncounterService(
    private val repo: EncounterRepository,
    private val hub: EncounterHub,
    private val tx: TransactionTemplate,
) {
    private class Loaded(val name: String, val campaignId: UUID, var status: EncounterStatus, var state: EncounterState, var revision: Long) {
        val header get() = Header(name, campaignId, status)

        /** The client-facing version is the encounter's revision, not the internal event count. */
        fun snapshot() = state.copy(version = revision)
    }

    private val cache = ConcurrentHashMap<UUID, Loaded>()
    private val locks = ConcurrentHashMap<UUID, ReentrantLock>()

    private fun <T> locked(id: UUID, block: () -> T): T = locks.computeIfAbsent(id) { ReentrantLock() }.withLock(block)

    private fun load(id: UUID): Loaded = cache[id] ?: run {
        val meta = repo.meta(id) ?: throw EncounterNotFound(id)
        Loaded(meta.name, meta.campaignId, meta.status, replay(id), meta.version).also { cache[id] = it }
    }

    private fun replay(id: UUID) = repo.events(id).fold(EMPTY_ENCOUNTER, ::evolve)

    fun view(id: UUID, actor: Actor): EncounterViewDto = locked(id) { load(id).let { viewFor(it.snapshot(), actor).toDto(it.header) } }

    fun status(id: UUID): EncounterStatus = locked(id) { load(id).status }

    private fun requireRunning(loaded: Loaded) {
        when (loaded.status) {
            EncounterStatus.PREPARING -> throw CommandRejected(Reason.INVALID, "This fight hasn't started yet.")
            EncounterStatus.ENDED -> throw CommandRejected(Reason.INVALID, "This fight is over.")
            else -> {}
        }
    }

    private fun requireDm(actor: Actor) {
        if (actor != Actor.Dm) throw CommandRejected(Reason.FORBIDDEN, "Only the DM can do that.")
    }

    private class Change(val header: Header, val snapshot: EncounterState)

    /** Runs `block` under the encounter's lock, then tells everyone watching (outside the lock, so a slow client can't hold it). */
    private fun publish(id: UUID, block: (Loaded) -> Change): Long {
        val change = locked(id) { block(load(id)) }
        hub.broadcast(id, change.snapshot, change.header)
        return change.snapshot.version
    }

    /**
     * Writes one batch of events (and maybe a new status) in one transaction. Call with the lock held.
     * `extra` runs in the same transaction with the new state, for writes outside the event log.
     */
    private fun commit(
        id: UUID,
        loaded: Loaded,
        actor: Actor,
        events: List<Event>,
        newStatus: EncounterStatus? = null,
        lock: Boolean = false,
        extra: (EncounterState) -> Unit = {},
    ): Change {
        val revision = loaded.revision + 1
        val next = events.fold(loaded.state, ::evolve)
        tx.executeWithoutResult {
            if (events.isNotEmpty()) repo.append(id, batch = revision, actor = actor, events = events)
            repo.setVersion(id, revision)
            if (newStatus != null) repo.setStatus(id, newStatus)
            if (lock) repo.lockBatches(id, revision)
            extra(next)
        }
        loaded.state = next
        loaded.revision = revision
        if (newStatus != null) loaded.status = newStatus
        return Change(loaded.header, loaded.snapshot())
    }

    private fun decideOrThrow(state: EncounterState, command: Command, actor: Actor): List<Event> =
        when (val d = decide(state, command, actor)) {
            is Decision.Rejected -> throw CommandRejected(d.reason, d.message)
            is Decision.Accepted -> d.events
        }

    /** Applies one command and returns the new revision. Throws on a stale version or a rejected command. */
    fun handle(id: UUID, actor: Actor, command: Command, expectedVersion: Long?): Long = publish(id) { loaded ->
        requireRunning(loaded)
        if (expectedVersion != null && expectedVersion != loaded.revision) throw StaleVersion()
        commit(id, loaded, actor, decideOrThrow(loaded.state, command, actor))
    }

    /** DM only: reverses the last command (all of its events) by replaying the log without it. */
    fun undo(id: UUID, actor: Actor): Long = publish(id) { loaded ->
        requireDm(actor)
        requireRunning(loaded)
        val revision = loaded.revision + 1
        val undone = tx.execute { repo.undoLastBatch(id).also { if (it) repo.setVersion(id, revision) } } ?: false
        if (!undone) throw CommandRejected(Reason.INVALID, "There is nothing to undo.")
        loaded.state = replay(id)
        loaded.revision = revision
        Change(loaded.header, loaded.snapshot())
    }

    /** Start combat: the prepared roster goes onto the tracker (for good: undo stops here) and initiative begins. */
    fun start(id: UUID, actor: Actor, combatants: List<Combatant>): Long = publish(id) { loaded ->
        requireDm(actor)
        if (loaded.status != EncounterStatus.PREPARING) throw CommandRejected(Reason.INVALID, "This fight has already started.")
        if (combatants.isEmpty()) throw CommandRejected(Reason.INVALID, "Add someone to the fight first.")
        val events = decideOrThrow(loaded.state, Command.AddCombatants(combatants), actor)
        commit(id, loaded, actor, events, newStatus = EncounterStatus.ROLLING_INITIATIVE, lock = true)
    }

    /** Initiative is in: round 1, first in order goes. */
    fun begin(id: UUID, actor: Actor): Long = publish(id) { loaded ->
        requireDm(actor)
        if (loaded.status != EncounterStatus.ROLLING_INITIATIVE) throw CommandRejected(Reason.INVALID, "Combat isn't waiting on initiative.")
        commit(id, loaded, actor, decideOrThrow(loaded.state, Command.NextTurn, actor), newStatus = EncounterStatus.ACTIVE)
    }

    /** End the fight. `writeBack` runs in the same transaction with the final tracker (e.g. to save party HP). */
    fun end(id: UUID, actor: Actor, writeBack: (EncounterState) -> Unit): Long = publish(id) { loaded ->
        requireDm(actor)
        requireRunning(loaded)
        commit(id, loaded, actor, emptyList(), newStatus = EncounterStatus.ENDED, extra = writeBack)
    }

    /** Drops the cached copy, e.g. after the encounter is deleted. */
    fun forget(id: UUID) = locked(id) { cache.remove(id); Unit }

    /** Opens a live stream for this viewer, starting with their current view. */
    fun subscribe(id: UUID, actor: Actor) = hub.subscribe(id, actor, view(id, actor))
}
