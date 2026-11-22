import type { LastTurnView } from '../api/types'

/** "Hit Goblin 2 · 9", "Miss", or "2 hits, 1 miss · 17". */
export function describeLastTurn(t: LastTurnView): string {
  if (t.attacks.length === 1) {
    const a = t.attacks[0]
    const target = a.targetName ? ` ${a.targetName}` : ''
    return a.hit ? `Hit${target} · ${a.damage}` : `Miss${target}`
  }
  const parts = []
  if (t.hits > 0) parts.push(`${t.hits} hit${t.hits > 1 ? 's' : ''}`)
  if (t.misses > 0) parts.push(`${t.misses} miss${t.misses > 1 ? 'es' : ''}`)
  return t.damage > 0 ? `${parts.join(', ')} · ${t.damage}` : parts.join(', ')
}

/** Up to two initials, for rows without a portrait. */
export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]!.toUpperCase())
    .join('')
}
