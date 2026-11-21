// Mirrors the Kotlin EncounterView (backend/.../encounter/domain/View.kt).
// Once the API exposes an OpenAPI spec, scripts/gen-api.sh generates these instead.

export type Side = 'PARTY' | 'ENEMY' | 'NEUTRAL'
export type Cost = 'ACTION' | 'BONUS' | 'REACTION' | 'FREE'
export type RollMode = 'NORMAL' | 'ADVANTAGE' | 'DISADVANTAGE'
export type Natural = 'TWENTY' | 'ONE'
export type AttackKind = 'ATTACK' | 'SPELL'
export type AttackStage = 'DECLARED' | 'RESUBMIT' | 'REACTION_WINDOW' | 'AWAITING_DAMAGE' | 'DAMAGE_SUBMITTED'

export interface Condition {
  name: string
  private: boolean
}

export interface Resources {
  actionUsed: boolean
  bonusUsed: boolean
  reactionUsed: boolean
  movementUsed: number
  dashed: boolean
  attacksThisTurn: number
}

/** Fields the viewer may not see arrive as null (e.g. enemy hp for players). */
export interface CombatantView {
  id: string
  name: string
  side: Side
  initiative: number
  isCurrent: boolean
  controlledByViewer: boolean
  hp: number | null
  maxHp: number | null
  ac: number | null
  conditions: Condition[]
  resources: Resources | null
  hidden: boolean
  defeated: boolean
}

export interface PendingView {
  attackerId: string
  targetId: string
  label: string
  kind: AttackKind
  roll: number
  rollMode: RollMode
  natural: Natural | null
  early: boolean
  stage: AttackStage
  damage: number | null
  damageType: string | null
  sendBackReason: string | null
  targetAc: number | null
}

export interface EncounterView {
  round: number
  currentId: string
  version: number
  combatants: CombatantView[]
  pending: PendingView | null
}
