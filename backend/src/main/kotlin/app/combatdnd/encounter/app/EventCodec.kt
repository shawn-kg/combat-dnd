package app.combatdnd.encounter.app

import app.combatdnd.encounter.domain.Event
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/** Turns events into (type, JSON) for encounter_event and back. The type is the event's class name. */
@Component
class EventCodec(private val mapper: ObjectMapper) {
    private val types = Event::class.sealedSubclasses.associateBy { it.simpleName!! }

    fun typeOf(event: Event): String = event::class.simpleName!!

    fun encode(event: Event): String = mapper.writeValueAsString(event)

    fun decode(type: String, json: String): Event {
        val kClass = types[type] ?: throw IllegalStateException("Unknown event type $type")
        return mapper.readValue(json, kClass.java)
    }
}
