package app.combatdnd

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CombatDndApplication

fun main(args: Array<String>) {
    runApplication<CombatDndApplication>(*args)
}
