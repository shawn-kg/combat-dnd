import { parseInitiative } from './initiative'
import { charactersNotInFight } from './partyMembers'
import { targetGroups } from './targets'
import { isDndBeyondLink } from './dndbeyond'
import { summaryRows, totalsLine } from './fightStats'
import { describe, expect, it } from 'vitest'
import type { AttackView, CombatantView, LastTurnView } from '../api/types'
import { applyHpEdit, numberedNames } from './hpEdit'
import { describeAttack, initials, totalLine } from './lastTurn'

describe('applyHpEdit', () => {
  it('takes damage, heals and sets', () => {
    expect(applyHpEdit('-5', 20, 30)).toBe(15)
    expect(applyHpEdit('+3', 20, 30)).toBe(23)
    expect(applyHpEdit('=12', 20, 30)).toBe(12)
    expect(applyHpEdit('12', 20, 30)).toBe(12)
  })
  it('stays between 0 and max', () => {
    expect(applyHpEdit('-50', 20, 30)).toBe(0)
    expect(applyHpEdit('+50', 20, 30)).toBe(30)
  })
  it('ignores anything else', () => {
    expect(applyHpEdit('', 20, 30)).toBeNull()
    expect(applyHpEdit('ten', 20, 30)).toBeNull()
    expect(applyHpEdit('-', 20, 30)).toBeNull()
  })
})

describe('numberedNames', () => {
  it('keeps a single new name plain when it is free', () => {
    expect(numberedNames('Wolf', 1, ['Goblin 1'])).toEqual(['Wolf'])
  })
  it('numbers several, after any that already exist', () => {
    expect(numberedNames('Goblin', 2, ['Goblin 1', 'Goblin 2', 'Bram'])).toEqual(['Goblin 3', 'Goblin 4'])
    expect(numberedNames('Wolf', 2, [])).toEqual(['Wolf 1', 'Wolf 2'])
  })
})

const attack = (over: Partial<AttackView>): AttackView => ({
  id: 'a1',
  round: 1,
  attackerId: 'boss',
  targetId: 'thorin',
  targetName: 'Thorin',
  hit: true,
  damage: 6,
  ...over,
})

describe('last turn lines', () => {
  it('describes hits, misses and hidden targets', () => {
    expect(describeAttack(attack({}))).toBe('Hit Thorin · 6')
    expect(describeAttack(attack({ hit: false, damage: 0 }))).toBe('Miss Thorin')
    expect(describeAttack(attack({ targetName: null }))).toBe('Hit · 6')
  })
  it('adds a total only for several attacks with damage', () => {
    const turn = (attacks: AttackView[]): LastTurnView => ({
      round: 1,
      hits: attacks.filter((a) => a.hit).length,
      misses: attacks.filter((a) => !a.hit).length,
      damage: attacks.reduce((s, a) => s + a.damage, 0),
      attacks,
    })
    expect(totalLine(turn([attack({})]))).toBeNull()
    expect(totalLine(turn([attack({}), attack({ id: 'a2', damage: 7 })]))).toBe('Total 13')
    expect(totalLine(turn([attack({ hit: false, damage: 0 }), attack({ id: 'a2', hit: false, damage: 0 })]))).toBeNull()
  })
  it('makes initials', () => {
    expect(initials('Goblin Archer')).toBe('GA')
    expect(initials('Lyra')).toBe('L')
  })
})

describe('parseInitiative', () => {
  it('takes whole numbers, including negatives, and treats anything else as not rolled', () => {
    expect(parseInitiative('17')).toBe(17)
    expect(parseInitiative(' -1 ')).toBe(-1)
    expect(parseInitiative('0')).toBe(0)
    expect(parseInitiative('')).toBeNull()
    expect(parseInitiative(undefined)).toBeNull()
    expect(parseInitiative('12.5')).toBeNull()
    expect(parseInitiative('abc')).toBeNull()
  })
})

describe('charactersNotInFight', () => {
  const ch = (id: string, name: string) => ({ id, name, portraitKey: null, ac: 15, hp: 20, maxHp: 20, playerId: null, playerName: null, dndBeyondUrl: null })
  it('offers only characters whose id is not already a combatant', () => {
    const view = { combatants: [{ id: 'lyra' }, { id: 'goblin-1' }] } as unknown as Parameters<typeof charactersNotInFight>[1]
    expect(charactersNotInFight([ch('lyra', 'Lyra'), ch('bram', 'Bram')], view).map((c) => c.name)).toEqual(['Bram'])
  })
})

describe('targetGroups', () => {
  const c = (id: string, side: 'PARTY' | 'ENEMY' | 'NPC', defeated = false) => ({ id, name: id, side, defeated }) as unknown as CombatantView
  const all = [c('lyra', 'PARTY'), c('bram', 'PARTY'), c('gob', 'ENEMY'), c('dead', 'ENEMY', true), c('mira', 'NPC')]
  const names = (g: ReturnType<typeof targetGroups>) => g.map(([h, list]) => `${h}: ${list.map((x) => x.id).join(',')}`)
  it('puts the likely targets first, leaves out the attacker, the defeated and empty groups', () => {
    expect(names(targetGroups(all[0], all))).toEqual(['Enemies: gob', 'NPCs: mira', 'Party: bram'])
    expect(names(targetGroups(all[2], all))).toEqual(['Party: lyra,bram', 'NPCs: mira'])
    expect(names(targetGroups(all[4], all))).toEqual(['Enemies: gob', 'Party: lyra,bram'])
  })
})

describe('isDndBeyondLink', () => {
  it('only makes links of the exact form the server stores', () => {
    expect(isDndBeyondLink('https://www.dndbeyond.com/characters/123456')).toBe(true)
    for (const bad of [null, undefined, '', 'https://www.dndbeyond.com/characters/', 'https://www.dndbeyond.com/characters/12/x',
      'https://www.dndbeyond.com.evil.example/characters/1', 'javascript:alert(1)', 'https://evil.example/characters/1'])
      expect(isDndBeyondLink(bad)).toBe(false)
  })
})

describe('fight totals', () => {
  const t = (dealt: number, taken: number, hits = 0, misses = 0) => ({ dealt, taken, hits, misses, biggestHit: 0 })
  it('says nothing until something happened, then dealt and taken', () => {
    expect(totalsLine(t(0, 0))).toBeNull()
    expect(totalsLine(t(0, 0, 0, 1))).toBe('Fight: dealt 0 · taken 0')
    expect(totalsLine(t(34, 12))).toBe('Fight: dealt 34 · taken 12')
  })
  it('orders the summary by damage dealt, then taken, then name', () => {
    const c = (name: string, dealt: number, taken: number) => ({ id: name, name, totals: t(dealt, taken) }) as unknown as CombatantView
    expect(summaryRows([c('Bram', 5, 0), c('Lyra', 34, 3), c('Abe', 5, 0), c('Gob', 5, 9)]).map((x) => x.name)).toEqual(['Lyra', 'Gob', 'Abe', 'Bram'])
  })
})
