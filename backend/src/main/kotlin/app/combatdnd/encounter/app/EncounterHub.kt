package app.combatdnd.encounter.app

import app.combatdnd.encounter.api.toDto
import app.combatdnd.encounter.domain.Actor
import app.combatdnd.encounter.domain.EncounterState
import app.combatdnd.encounter.domain.EncounterView
import app.combatdnd.encounter.domain.viewFor
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Live connections per encounter. After every change each viewer gets their own filtered snapshot
 * (the whole tracker, a few KB), so clients never merge diffs and a reconnect is just a fresh snapshot.
 */
@Component
class EncounterHub {
    private class Subscriber(val actor: Actor, val emitter: SseEmitter)

    private val log = LoggerFactory.getLogger(javaClass)
    private val subscribers = ConcurrentHashMap<UUID, CopyOnWriteArrayList<Subscriber>>()

    fun subscribe(id: UUID, actor: Actor, initial: EncounterView): SseEmitter {
        val emitter = SseEmitter(0L) // no server-side timeout; the heartbeat keeps proxies from closing it
        val sub = Subscriber(actor, emitter)
        val list = subscribers.computeIfAbsent(id) { CopyOnWriteArrayList() }
        list += sub
        val remove = Runnable { list.remove(sub) }
        emitter.onCompletion(remove)
        emitter.onTimeout(remove)
        emitter.onError { remove.run() }
        send(sub, initial)
        return emitter
    }

    fun broadcast(id: UUID, state: EncounterState) {
        subscribers[id]?.forEach { send(it, viewFor(state, it.actor)) }
    }

    fun connectionCount(id: UUID): Int = subscribers[id]?.size ?: 0

    private fun send(sub: Subscriber, view: EncounterView) {
        try {
            sub.emitter.send(SseEmitter.event().name("state").data(view.toDto()))
        } catch (e: Exception) {
            log.debug("Dropping a closed stream: {}", e.message)
            sub.emitter.completeWithError(e)
        }
    }

    @Scheduled(fixedRate = 15_000)
    fun heartbeat() {
        subscribers.values.forEach { list ->
            list.forEach { sub ->
                try {
                    sub.emitter.send(SseEmitter.event().comment("keep-alive"))
                } catch (e: Exception) {
                    sub.emitter.completeWithError(e)
                }
            }
        }
    }
}
