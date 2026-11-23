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
  /** A player runs it, rather than the DM. */
  runByPlayer: boolean
  ac: number | null
  hp: number | null
  maxHp: number | null
  hidden: boolean
  defeated: boolean
  lastTurn: LastTurnView | null
}

/** PREPARING: the DM is setting it up. ROLLING_INITIATIVE: on the tracker, initiative going in. ACTIVE: turns running. ENDED: over. */
export type EncounterStatus = 'PREPARING' | 'ROLLING_INITIATIVE' | 'ACTIVE' | 'ENDED'

export interface EncounterView {
  campaignId: string
  name: string
  status: EncounterStatus
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
