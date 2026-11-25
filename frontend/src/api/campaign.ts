// Mirrors backend/.../campaign/CampaignDtos.kt and encounter/prep/PrepDtos.kt.

import type { EncounterStatus } from './types'

export type PlayerStatus = 'PENDING' | 'APPROVED' | 'REMOVED'

export interface PlayerDto {
  id: string
  name: string
  status: PlayerStatus
  characterId: string | null
}

export interface CharacterDto {
  id: string
  name: string
  portraitKey: string | null
  ac: number
  hp: number
  maxHp: number
  playerId: string | null
  playerName: string | null
  /** Their D&D Beyond sheet, always https://www.dndbeyond.com/characters/<id>. */
  dndBeyondUrl: string | null
}

/** The DM's other people: neither party nor monsters. HP carries over like a character's. */
export interface NpcDto {
  id: string
  name: string
  portraitKey: string | null
  ac: number
  hp: number
  maxHp: number
  /** Whether players see its AC and HP on the tracker. */
  statsVisible: boolean
}

export interface TemplateDto {
  id: string
  name: string
  portraitKey: string | null
  ac: number
  maxHp: number
}

export interface CampaignView {
  id: string
  name: string
  role: 'DM' | 'PLAYER'
  /** DM only. */
  joinCode: string | null
  players: PlayerDto[]
  characters: CharacterDto[]
  /** The signed-in player; null for the DM. */
  me: PlayerDto | null
  /** DM only: monster templates. */
  templates: TemplateDto[]
  /** The DM sees every encounter, newest first; players only see fights under way. */
  encounters: EncounterSummary[]
  /** DM only: whether a DM sign-in link exists (it's only shown when made), and how many devices are signed in as DM. */
  dmLinkMade: boolean
  dmDevices: number
  /** DM only. */
  npcs: NpcDto[]
}

export interface DmLinkInfo {
  campaignId: string
  campaignName: string
}

export interface EncounterSummary {
  id: string
  name: string
  status: EncounterStatus
}

export interface PrepPartyMember {
  characterId: string
  name: string
  portraitKey: string | null
  ac: number
  hp: number
  maxHp: number
  playerName: string | null
  included: boolean
}

export interface PrepEnemy {
  templateId: string
  name: string
  portraitKey: string | null
  ac: number
  maxHp: number
  count: number
  hidden: boolean
}

export interface PrepView {
  id: string
  campaignId: string
  name: string
  status: EncounterStatus
  party: PrepPartyMember[]
  enemies: PrepEnemy[]
  /** Every NPC in the campaign; `included` ones are in this fight. */
  npcs: PrepNpc[]
}

export interface PrepNpc {
  npcId: string
  name: string
  portraitKey: string | null
  ac: number
  hp: number
  maxHp: number
  statsVisible: boolean
  included: boolean
  hidden: boolean
}

export interface MyCampaign {
  id: string
  name: string
  role: 'DM' | 'PLAYER'
}

export interface JoinInfo {
  campaignId: string
  campaignName: string
  /** Approved players, for "I'm returning as…". */
  players: { id: string; name: string }[]
}
