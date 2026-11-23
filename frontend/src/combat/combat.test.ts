import { describe, expect, it } from 'vitest'
import type { AttackView, LastTurnView } from '../api/types'
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
