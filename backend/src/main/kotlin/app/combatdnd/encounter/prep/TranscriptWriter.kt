package app.combatdnd.encounter.prep

import app.combatdnd.encounter.app.EncounterStatus
import app.combatdnd.encounter.domain.Side
import app.combatdnd.encounter.domain.Totals
import app.combatdnd.encounter.domain.Transcript
import app.combatdnd.encounter.domain.label
import java.time.LocalDate

/** What a transcript is about, beyond its events. */
data class TranscriptInfo(val encounter: String, val campaign: String, val status: EncounterStatus, val playedOn: LocalDate?)

/** Writes a replayed fight as Markdown (to read or paste into Discord or notes) or CSV (one row per attack). */
object TranscriptWriter {

    fun markdown(t: Transcript, info: TranscriptInfo): String = buildString {
        val state = t.final
        appendLine("# ${md(info.encounter)}: fight transcript")
        appendLine()
        val when_ = listOfNotNull(md(info.campaign), info.playedOn?.toString(), rounds(state.round),
            if (info.status == EncounterStatus.ENDED) "over" else "in progress (so far)")
        appendLine(when_.joinToString(" · "))

        var round = -1
        var inTurn = false // lines before the round's first turn (like the starting order) aren't under a turn
        for (line in t.lines) {
            if (line.round != round && line.round > 0) {
                round = line.round
                inTurn = false
                appendLine()
                appendLine("## Round $round")
                appendLine()
            }
            if (line.turn) inTurn = true
            appendLine(if (line.turn) "- **${md(line.text)}**" else if (inTurn) "  - ${md(line.text)}" else "- ${md(line.text)}")
        }

        // Summary: every combatant still in the fight, by damage dealt.
        val attacks = state.attacks.filter { !it.deleted }
        val rows = state.order.map(state::combatant).map { it to Totals.of(it.id, attacks) }.sortedByDescending { it.second.dealt }
        if (rows.isNotEmpty()) {
            appendLine()
            appendLine("## Summary")
            appendLine()
            appendLine("| Combatant | Side | Dealt | Taken | Hits | Misses | Biggest hit | HP at end |")
            appendLine("| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |")
            rows.forEach { (c, s) ->
                val end = "${c.hp}/${c.maxHp}" + when {
                    c.defeated -> " (defeated)"
                    c.hp == 0 -> " (down)"
                    else -> ""
                }
                appendLine("| ${cell(c.name)} | ${c.side.label()} | ${s.dealt} | ${s.taken} | ${s.hits} | ${s.misses} | ${s.biggestHit} | $end |")
            }
            rows.firstOrNull { it.second.dealt > 0 }?.let { (c, s) ->
                appendLine()
                appendLine("Most damage dealt: **${md(c.name)}** (${s.dealt}).")
            }
        }
        appendLine()
        appendLine("_Damage counts as logged. HP typed in directly (traps, spells) appears above as HP changes, not as damage._")
    }

    fun csv(t: Transcript): String = buildString {
        appendLine("round,attacker,target,result,damage,target_hp_after,target_max_hp,corrected")
        t.attacks.forEach { a ->
            appendLine(
                listOf(a.round.toString(), a.attacker, a.target, if (a.hit) "hit" else "miss", a.damage.toString(),
                    a.targetHp.toString(), a.targetMaxHp.toString(), if (a.corrected) "yes" else "no")
                    .joinToString(",", transform = ::csvCell),
            )
        }
    }

    private fun rounds(n: Int) = if (n == 1) "1 round" else "$n rounds"

    /** Names are typed by people: keep them from turning into Markdown formatting. */
    private fun md(s: String) = s.replace(Regex("""([\\`*_\[\]<>#|~])"""), """\\$1""")

    private fun cell(s: String) = md(s)

    /**
     * RFC 4180 quoting, plus a guard against spreadsheet formulas: a name like `=HYPERLINK(...)` would otherwise
     * run as a formula when the CSV is opened in Excel or Sheets.
     */
    internal fun csvCell(s: String): String {
        val safe = if (s.isNotEmpty() && s[0] in "=+-@\t\r") "'$s" else s
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
