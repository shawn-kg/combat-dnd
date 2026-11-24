import { useState } from 'react'
import type { Command, EncounterView } from '../api/types'
import { parseInitiative } from './initiative'
import { Portrait } from './Portrait'
import { Steps } from './Steps'
import type { SendResult } from './useEncounter'
import './tracker.css'

/**
 * The DM types everyone's initiative roll, sorts, breaks ties with the arrows, and begins round 1.
 * Rolls are kept here until Sort (or Begin) sends them all at once, so rows don't jump around while typing.
 */
export function Initiative({
  view,
  send,
  begin,
}: {
  view: EncounterView
  send: (command: Command) => Promise<SendResult>
  begin: () => Promise<SendResult>
}) {
  // Everyone starts at 0 on the server, which here means "not rolled yet".
  const [draft, setDraft] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const rows = view.combatants
  const valueOf = (id: string, server: number) => draft[id] ?? (server === 0 ? '' : String(server))
  const changed = rows.filter((c) => {
    const n = parseInitiative(draft[c.id])
    return n !== null && n !== c.initiative
  })

  async function run(step: () => Promise<SendResult>) {
    setBusy(true)
    const r = await step()
    setBusy(false)
    setError(r.ok ? null : r.message)
    return r.ok
  }

  const saveRolls = () =>
    run(() => send({ type: 'SetInitiatives', initiatives: Object.fromEntries(changed.map((c) => [c.id, parseInitiative(draft[c.id])])) }))

  async function sort() {
    if (changed.length === 0) return run(() => send({ type: 'SetInitiatives', initiatives: Object.fromEntries(rows.map((c) => [c.id, c.initiative])) }))
    return saveRolls()
  }

  function move(i: number, by: -1 | 1) {
    const order = rows.map((c) => c.id)
    ;[order[i], order[i + by]] = [order[i + by], order[i]]
    run(() => send({ type: 'Reorder', order }))
  }

  async function start() {
    const missing = rows.filter((c) => parseInitiative(valueOf(c.id, c.initiative)) === null)
    if (missing.length) return setError(`Enter initiative for ${missing.map((c) => c.name).join(', ')}.`)
    if (changed.length > 0 && !(await saveRolls())) return
    await run(begin)
  }

  return (
    <section className="stack" aria-label="Roll initiative">
      <div className="card">
        <div className="lrow">
          <h3>Roll initiative · {view.name}</h3>
          <span className="spacer" />
          <Steps on="initiative" />
        </div>
        <p className="muted small">
          Players can send their own rolls; they appear here as they come in. Type the rest (you can type over any roll), then
          sort. Use the arrows to break ties. Players see the tracker once you begin.
        </p>
        <div className="lrow">
          <button type="button" className="btn small" disabled={busy} onClick={sort}>
            Sort by initiative
          </button>
          {changed.length > 0 && <span className="muted small">Sort to put the new rolls in order.</span>}
        </div>
      </div>

      <ol className="ini-list">
        {rows.map((c, i) => (
          <li key={c.id} className={`ini ${c.side === 'ENEMY' ? 'enemy' : c.side === 'NPC' ? 'npc' : 'party'}`}>
            <Portrait c={c} />
            <span className="grow">
              <span className="nm">{c.name}</span> {c.hidden && <span className="tag">Hidden</span>}
              <br />
              <span className="muted small">
                {c.side === 'ENEMY'
                  ? 'Enemy'
                  : c.side === 'NPC'
                    ? 'NPC · run by you'
                  : !c.runByPlayer
                    ? 'Party · run by you'
                    : c.initiative !== 0 && draft[c.id] === undefined
                      ? 'Rolled by their player'
                      : 'Waiting for their player (or type it)'}
              </span>
            </span>
            <input
              type="number"
              inputMode="numeric"
              className={`field n${error && parseInitiative(valueOf(c.id, c.initiative)) === null ? ' missing' : ''}`}
              placeholder="—"
              aria-label={`Initiative for ${c.name}`}
              value={valueOf(c.id, c.initiative)}
              onChange={(e) => setDraft((d) => ({ ...d, [c.id]: e.target.value }))}
              onKeyDown={(e) => {
                // Enter moves down the list, like a spreadsheet.
                if (e.key !== 'Enter') return
                const inputs = [...document.querySelectorAll<HTMLInputElement>('.ini-list input')]
                inputs[inputs.indexOf(e.currentTarget) + 1]?.focus()
              }}
            />
            <span className="stepper" title={changed.length > 0 ? 'Sort first, then fix ties' : undefined}>
              <button type="button" aria-label={`Move ${c.name} up`} disabled={busy || i === 0 || changed.length > 0} onClick={() => move(i, -1)}>
                ↑
              </button>
              <button
                type="button"
                aria-label={`Move ${c.name} down`}
                disabled={busy || i === rows.length - 1 || changed.length > 0}
                onClick={() => move(i, 1)}
              >
                ↓
              </button>
            </span>
          </li>
        ))}
      </ol>

      {error && (
        <div className="err" role="alert">
          {error}
        </div>
      )}
      <div className="lrow">
        <span className="spacer" />
        <button type="button" className="btn primary" disabled={busy} onClick={start}>
          Begin round 1 ▶
        </button>
      </div>
    </section>
  )
}
