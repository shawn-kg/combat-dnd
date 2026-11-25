import type { CombatantView, Totals } from '../api/types'

/** "Fight: dealt 34 · taken 12", or null before anything has happened to this combatant. */
export function totalsLine(t: Totals): string | null {
  if (t.dealt === 0 && t.taken === 0 && t.hits === 0 && t.misses === 0) return null
  return `Fight: dealt ${t.dealt} · taken ${t.taken}`
}

/** Summary rows, most damage dealt first (ties: most taken, then name). */
export function summaryRows(combatants: CombatantView[]): CombatantView[] {
  return [...combatants].sort((a, b) => b.totals.dealt - a.totals.dealt || b.totals.taken - a.totals.taken || a.name.localeCompare(b.name))
}

/** Where the transcript downloads from (DM only; the server checks). */
export const transcriptUrl = (encounterId: string, format: 'md' | 'csv') => `/api/encounters/${encounterId}/transcript?format=${format}`
