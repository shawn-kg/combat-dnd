import { useState } from 'react'
import type { EncounterView } from '../api/types'
import type { Act } from './Tracker'

/** DM only: recent entries, newest first, each editable or deletable. HP follows whatever changes. */
export function TurnLog({ view, act }: { view: EncounterView; act: Act }) {
  const [editing, setEditing] = useState<{ id: string; hit: boolean; damage: string } | null>(null)
  const names = new Map(view.combatants.map((c) => [c.id, c.name]))
  const entries = view.attacks.slice(0, 15)

  async function save() {
    if (!editing) return
    const dmg = editing.hit ? Number(editing.damage) : 0
    if (editing.hit && (!Number.isInteger(dmg) || dmg < 0)) return
    if (await act({ type: 'EditAttack', attackId: editing.id, hit: editing.hit, damage: dmg })) setEditing(null)
  }

  return (
    <div className="card">
      <h3>Turn log</h3>
      <div className="entries">
        {entries.length === 0 && <span className="muted">Nothing logged yet.</span>}
        {entries.map((a) => (
          <div key={a.id} className="entry">
            <span className="rd">R{a.round}</span>
            <span>
              <b>{names.get(a.attackerId) ?? 'Removed'}</b> → {a.targetName ?? 'Removed'}
            </span>
            {editing?.id === a.id ? (
              <>
                <button type="button" className="chip hitc" aria-pressed={editing.hit} onClick={() => setEditing({ ...editing, hit: true })}>
                  Hit
                </button>
                <button type="button" className="chip missc" aria-pressed={!editing.hit} onClick={() => setEditing({ ...editing, hit: false })}>
                  Miss
                </button>
                {editing.hit && (
                  <input
                    className="field n num"
                    type="number"
                    min={0}
                    aria-label="Damage"
                    autoFocus
                    value={editing.damage}
                    onChange={(e) => setEditing({ ...editing, damage: e.target.value })}
                    onKeyDown={(e) => e.key === 'Enter' && save()}
                  />
                )}
                <span className="sp" />
                <button type="button" className="btn small" onClick={() => setEditing(null)}>
                  Cancel
                </button>
                <button type="button" className="btn small primary" onClick={save}>
                  Save
                </button>
              </>
            ) : (
              <>
                <span>
                  <span className={a.hit ? 'hit' : 'miss'}>{a.hit ? 'Hit' : 'Miss'}</span>
                  {a.hit && <span className="dmg num"> {a.damage}</span>}
                </span>
                <span className="sp" />
                <button type="button" className="btn small" onClick={() => setEditing({ id: a.id, hit: a.hit, damage: String(a.damage) })}>
                  Edit
                </button>
                <button type="button" className="btn small danger" onClick={() => act({ type: 'DeleteAttack', attackId: a.id })}>
                  Delete
                </button>
              </>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
