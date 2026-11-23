import type { CharacterDto } from '../api/campaign'
import type { EncounterView } from '../api/types'

/** Campaign characters who aren't in this fight yet (party combatants use their character's id). */
export function charactersNotInFight(characters: CharacterDto[], view: Pick<EncounterView, 'combatants'>): CharacterDto[] {
  const inFight = new Set(view.combatants.map((c) => c.id))
  return characters.filter((ch) => !inFight.has(ch.id))
}
