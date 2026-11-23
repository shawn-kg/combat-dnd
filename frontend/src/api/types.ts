// Mirrors EncounterViewDto in backend/.../encounter/api/EncounterDtos.kt.

export type Side = 'PARTY' | 'ENEMY'

export interface AttackView {
  id: string
  round: number
  attackerId: string
  targetId: string
  /** Null when the target was removed or is hidden from this viewer. */
  targetName: string | null
  hit: boolean
  damage: number
}

export interface LastTurnView {
  round: number
  hits: number
  misses: number
  damage: number
  attacks: AttackView[]
}

/** Fields the viewer may not see arrive as null (enemy AC and HP for players). */
export interface CombatantView {
  id: string
  name: string
  side: Side
  portraitKey: string | null
  initiative: number
  isCurrent: boolean
  controlledByViewer: boolean
  ac: number | null
  hp: number | null
  maxHp: number | null
  hidden: boolean
  defeated: boolean
  lastTurn: LastTurnView | null
}

export interface EncounterView {
  name: string
  /** Decides which controls to show; the server still checks every command. */
  viewerIsDm: boolean
  round: number
  currentId: string | null
  version: number
  combatants: CombatantView[]
  /** Newest first. */
  attacks: AttackView[]
}

/** A tracker command, e.g. { type: 'LogAttack', attackerId, targetId, hit, damage }. */
export type Command = { type: string } & Record<string, unknown>
