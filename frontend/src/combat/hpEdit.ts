/**
 * The DM's HP box: "-5" takes 5, "+3" heals 3, "=20" or "20" sets it to 20. Always kept between 0 and max.
 * Returns null when the text isn't one of those, so the edit is simply cancelled.
 */
export function applyHpEdit(raw: string, hp: number, max: number): number | null {
  const m = raw.trim().match(/^([+\-=])?\s*(\d+)$/)
  if (!m) return null
  const n = Number(m[2])
  const next = m[1] === '-' ? hp - n : m[1] === '+' ? hp + n : n
  return Math.max(0, Math.min(max, next))
}

/** "Goblin" × 3 with "Goblin 1" already present → Goblin 2, Goblin 3, Goblin 4. A single new one keeps its plain name if free. */
export function numberedNames(base: string, count: number, existing: string[]): string[] {
  const name = base.trim()
  const taken = new Set(existing)
  if (count === 1 && !taken.has(name)) return [name]
  const used = existing
    .map((n) => n.match(new RegExp(`^${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')} (\\d+)$`)))
    .filter((m): m is RegExpMatchArray => m !== null)
    .map((m) => Number(m[1]))
  let next = used.length ? Math.max(...used) + 1 : 1
  return Array.from({ length: count }, () => `${name} ${next++}`)
}
