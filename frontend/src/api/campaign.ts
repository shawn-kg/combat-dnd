// Mirrors backend/.../campaign/CampaignDtos.kt.

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
