import { useState } from 'react'
import type { CombatantView, EncounterView } from '../api/types'
import type { Act } from './Tracker'
import { Portrait } from './Portrait'
import { targetGroups } from './targets'

/**
 * Logging a turn, all in one place: pick a target, Hit or Miss, damage, Log. "End turn" sits beside "Log"
 * and logs anything filled in but not logged yet before ending the turn.
 */
export function LogPanel({
  attacker,
  view,
  act,
  onClose,
}: {
  attacker: CombatantView
  view: EncounterView
  act: Act
  onClose: () => void
}) {
  const [targetId, setTargetId] = useState<string | null>(null)
  const [hit, setHit] = useState(true)
  const [damage, setDamage] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const groups = targetGroups(attacker, view.combatants)
  const thisTurn = view.attacks.filter((a) => a.attackerId === attacker.id && a.round === view.round)
  const filledIn = targetId !== null && (!hit || damage !== '')

  async function log(): Promise<boolean> {
    if (!targetId) return fail('Pick a target.')
    const dmg = hit ? Number(damage) : 0
    if (hit && (damage === '' || !Number.isInteger(dmg) || dmg < 0)) return fail('Enter the damage.')
    setError(null)
    const ok = await act({ type: 'LogAttack', attackerId: attacker.id, targetId, hit, damage: dmg })
    if (ok) {
      setTargetId(null)
      setDamage('')
    }
    return ok
  }

  function fail(message: string) {
    setError(message)
    return false
  }

  async function run(work: () => Promise<unknown>) {
    setBusy(true)
    try {
      await work()
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="logp">
      <div className="k">Log attack · {attacker.name}</div>
      <div className="tlist" role="list" aria-label="Targets">
        {groups.map(([heading, list]) => (
            <div key={heading} role="presentation">
              <div className="tgroup">{heading}</div>
              {list.map((c) => (
                <button
                  key={c.id}
                  type="button"
                  className="titem"
                  aria-pressed={targetId === c.id}
                  onClick={() => setTargetId(c.id)}
                >
                  <Portrait c={c} size="mini" />
                  <span className="tn">{c.name}</span>
                  <span className="tm">
                    {c.ac !== null && c.hp !== null ? `AC ${c.ac} · ${c.hp}/${c.maxHp}` : ''}
                    {c.hidden ? ' · hidden' : ''}
                  </span>
                </button>
              ))}
            </div>
          ))}
      </div>
      <div className="lrow">
        <span className="lbl">Result</span>
        <button type="button" className="chip hitc" aria-pressed={hit} onClick={() => setHit(true)}>
          Hit
        </button>
        <button type="button" className="chip missc" aria-pressed={!hit} onClick={() => setHit(false)}>
          Miss
        </button>
        {hit && (
          <input
            className="field n num"
            type="number"
            min={0}
            inputMode="numeric"
            aria-label="Damage"
            placeholder="Damage"
            value={damage}
            onChange={(e) => setDamage(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && run(log)}
          />
        )}
        <span className="spacer" />
        <button type="button" className="btn" onClick={onClose}>
          Done
        </button>
        <button type="button" className="btn primary" disabled={busy} onClick={() => run(log)}>
          Log
        </button>
        {attacker.isCurrent && (
          <button
            type="button"
            className="btn"
            disabled={busy}
            title="Logs anything filled in, then ends the turn"
            onClick={() =>
              run(async () => {
                if (filledIn && !(await log())) return
                if (await act({ type: 'NextTurn' })) onClose()
              })
            }
          >
            End turn ▶
          </button>
        )}
      </div>
      {error && <div className="err">{error}</div>}
      {thisTurn.length > 0 && (
        <>
          <div className="k">This turn</div>
          <div className="entries">
            {thisTurn.map((a) => (
              <div key={a.id} className="entry">
                <span>
                  <span className={a.hit ? 'hit' : 'miss'}>{a.hit ? 'Hit' : 'Miss'}</span> {a.targetName ?? '—'}
                </span>
                {a.hit && <span className="dmg num">{a.damage}</span>}
                <span className="sp" />
                <button type="button" className="btn small danger" onClick={() => act({ type: 'DeleteAttack', attackId: a.id })}>
                  Remove
                </button>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
