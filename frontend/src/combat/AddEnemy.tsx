import { useState, type ChangeEvent } from 'react'
import type { EncounterView } from '../api/types'
import { numberedNames } from './hpEdit'
import type { Act } from './Tracker'

/** DM only: add enemies mid-fight. Templates arrive with prep (milestone 4); for now type the stats. */
export function AddEnemy({ view, act, onClose }: { view: EncounterView; act: Act; onClose: () => void }) {
  const [f, setF] = useState({ name: '', initiative: '', ac: '', hp: '', count: '1', hidden: false })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const set = (k: keyof typeof f) => (e: ChangeEvent<HTMLInputElement>) =>
    setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  async function add() {
    const [init, ac, hp, count] = [f.initiative, f.ac, f.hp, f.count].map(Number)
    if (!f.name.trim()) return setError('Give it a name.')
    if ([f.initiative, f.ac, f.hp].some((v) => v.trim() === '')) return setError('Enter initiative, AC and HP.')
    if (![init, ac, hp, count].every(Number.isInteger) || hp <= 0 || ac < 0 || count < 1 || count > 12)
      return setError('Enter initiative, AC, HP (above 0) and how many (1–12).')
    setError(null)
    setBusy(true)
    const names = numberedNames(f.name, count, view.combatants.map((c) => c.name))
    // One command for the whole group, so a single Undo removes them all.
    const ok = await act({
      type: 'AddCombatants',
      combatants: names.map((name) => ({
        id: crypto.randomUUID(),
        name,
        side: 'ENEMY',
        initiative: init,
        ac,
        hp,
        maxHp: hp,
        hidden: f.hidden,
      })),
    })
    setBusy(false)
    if (ok) onClose()
  }

  return (
    <div className="card">
      <h3>Add enemies</h3>
      <div className="add-grid">
        <label>
          Name
          <input value={f.name} onChange={set('name')} placeholder="Goblin" autoFocus />
        </label>
        <label>
          Initiative
          <input type="number" value={f.initiative} onChange={set('initiative')} />
        </label>
        <label>
          AC
          <input type="number" value={f.ac} onChange={set('ac')} />
        </label>
        <label>
          HP
          <input type="number" value={f.hp} onChange={set('hp')} />
        </label>
        <label>
          How many
          <input type="number" min={1} max={12} value={f.count} onChange={set('count')} />
        </label>
        <label className="check">
          <input type="checkbox" checked={f.hidden} onChange={set('hidden')} /> Hidden from players
        </label>
      </div>
      {error && <div className="err">{error}</div>}
      <div className="lrow">
        <span className="spacer" />
        <button type="button" className="btn" onClick={onClose}>
          Cancel
        </button>
        <button type="button" className="btn primary" disabled={busy} onClick={add}>
          Add
        </button>
      </div>
    </div>
  )
}
