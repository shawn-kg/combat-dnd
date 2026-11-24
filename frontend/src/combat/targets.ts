import type { CombatantView, Side } from '../api/types'

const LABEL: Record<Side, string> = { PARTY: 'Party', ENEMY: 'Enemies', NPC: 'NPCs' }

/** Most likely targets first: enemies attack the party, everyone else attacks enemies; NPCs sit in between. */
const ORDER: Record<Side, Side[]> = {
  ENEMY: ['PARTY', 'NPC', 'ENEMY'],
  PARTY: ['ENEMY', 'NPC', 'PARTY'],
  NPC: ['ENEMY', 'PARTY', 'NPC'],
}

/** The Log attack target list, grouped by side (empty groups left out). */
export function targetGroups(attacker: Pick<CombatantView, 'id' | 'side'>, combatants: CombatantView[]): [string, CombatantView[]][] {
  const candidates = combatants.filter((c) => c.id !== attacker.id && !c.defeated)
  return ORDER[attacker.side]
    .map((side): [string, CombatantView[]] => [LABEL[side], candidates.filter((c) => c.side === side)])
    .filter(([, list]) => list.length > 0)
}
