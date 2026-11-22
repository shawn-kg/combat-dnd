import type { AttackView, LastTurnView } from '../api/types'

/** One line per attack: "Hit Goblin 2 · 9" or "Miss Goblin 1". The target is left out when the viewer can't see it. */
export function describeAttack(a: AttackView): string {
  const target = a.targetName ? ` ${a.targetName}` : ''
  return a.hit ? `Hit${target} · ${a.damage}` : `Miss${target}`
}

/** A total line only when the turn had more than one attack and did some damage. */
export function totalLine(t: LastTurnView): string | null {
  return t.attacks.length > 1 && t.damage > 0 ? `Total ${t.damage}` : null
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
