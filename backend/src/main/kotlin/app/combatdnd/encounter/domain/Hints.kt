package app.combatdnd.encounter.domain

/** A reminder shown before rolling. Display only: the app cannot see distance or line of sight. */
data class Hint(val text: String, val private: Boolean)

private val attackerConditions = mapOf(
    "Poisoned" to "disadvantage on attack rolls",
    "Blinded" to "disadvantage on attack rolls",
    "Prone" to "disadvantage on attack rolls",
    "Restrained" to "disadvantage on attack rolls",
    "Frightened" to "disadvantage while the source of fear is in sight",
)

private val targetConditions = mapOf(
    "Prone" to "advantage for melee within 5 ft, disadvantage for ranged",
    "Restrained" to "attacks against it have advantage",
    "Blinded" to "attacks against it have advantage",
    "Stunned" to "attacks against it have advantage",
    "Paralyzed" to "advantage, and hits within 5 ft are critical",
    "Unconscious" to "advantage, and hits within 5 ft are critical",
)

/** Hints come only from conditions this viewer can already see. */
fun hintsFor(attacker: Combatant, target: Combatant, viewer: Actor): List<Hint> {
    val own = viewer != Actor.Dm && viewer.controls(attacker)
    val fromAttacker = visibleConditions(attacker, viewer).mapNotNull { cond ->
        attackerConditions[cond.name]?.let { effect ->
            val who = if (own) "You are" else "${attacker.name} is"
            Hint("$who ${cond.name}: $effect", cond.private)
        }
    }
    val fromTarget = visibleConditions(target, viewer).mapNotNull { cond ->
        targetConditions[cond.name]?.let { effect -> Hint("${target.name} is ${cond.name}: $effect", cond.private) }
    }
    return fromAttacker + fromTarget
}
