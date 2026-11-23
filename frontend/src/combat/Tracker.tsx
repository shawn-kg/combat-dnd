import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import type { CombatantView, Command, EncounterView } from '../api/types'
import { AddEnemy } from './AddEnemy'
import { Editable } from './Editable'
import { applyHpEdit } from './hpEdit'
import { describeAttack, totalLine } from './lastTurn'
import { LogPanel } from './LogPanel'
import { Portrait } from './Portrait'
import { TurnLog } from './TurnLog'
import type { SendResult } from './useEncounter'
import './tracker.css'

/** Sends a command; on failure shows the reason and resolves false. */
export type Act = (command: Command) => Promise<boolean>

/** The initiative tracker: one row per combatant, in initiative order. The DM gets editing and controls. */
export function Tracker({
  view,
  connected,
  send,
  undo,
}: {
  view: EncounterView
  connected: boolean
  send: (command: Command) => Promise<SendResult>
  undo: () => Promise<SendResult>
}) {
  const [logFor, setLogFor] = useState<string | null>(null)
  const [menuFor, setMenuFor] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const noticeTimer = useRef<number | undefined>(undefined)
  const dm = view.viewerIsDm

  const show = useCallback((message: string) => {
    setNotice(message)
    window.clearTimeout(noticeTimer.current)
    noticeTimer.current = window.setTimeout(() => setNotice(null), 5000)
  }, [])

  const act: Act = useCallback(
    async (command) => {
      const r = await send(command)
      if (!r.ok) show(r.message)
      return r.ok
    },
    [send, show],
  )

  const doUndo = useCallback(async () => {
    const r = await undo()
    if (!r.ok) show(r.message)
  }, [undo, show])

  // A player's log panel closes when their turn ends.
  const logAttacker = view.combatants.find((c) => c.id === logFor)
  const logOpen = logAttacker && (dm || logAttacker.isCurrent)

  // DM shortcuts: N next turn, U undo (not while typing).
  useEffect(() => {
    if (!dm) return
    const onKey = (e: KeyboardEvent) => {
      const el = e.target as HTMLElement
      if (/^(INPUT|SELECT|TEXTAREA)$/.test(el.tagName) || e.metaKey || e.ctrlKey || e.altKey) return
      if (e.key === 'n') act({ type: 'NextTurn' })
      if (e.key === 'u') doUndo()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [dm, act, doUndo])

  const current = view.combatants.find((c) => c.isCurrent)
  const turnText = !current ? 'Combat hasn’t started' : current.controlledByViewer ? 'Your turn' : `${current.name}’s turn`

  return (
    <section className={`tracker ${dm ? 'dm' : 'pl'}`} aria-label="Initiative tracker">
      <div className="bar">
        <span className="who-view">{dm ? 'DM' : 'Player'}</span>
        <span className="state">
          <b>{view.name}</b> · Round {view.round} · {turnText}
        </span>
        {!connected && <span className="offline">Reconnecting…</span>}
        <span className="spacer" />
        {dm && (
          <>
            <button type="button" className="btn small" onClick={doUndo}>
              ↶ Undo<kbd>U</kbd>
            </button>
            <button type="button" className="btn small" onClick={() => setAdding(!adding)}>
              + Add enemies
            </button>
            <button type="button" className="btn small" onClick={() => act({ type: 'PreviousTurn' })}>
              ◀ Previous
            </button>
            <button type="button" className="btn small primary" onClick={() => act({ type: 'NextTurn' })}>
              Next turn ▶<kbd>N</kbd>
            </button>
          </>
        )}
      </div>

      {notice && (
        <div className="notice" role="alert">
          {notice}
        </div>
      )}
      {dm && adding && <AddEnemy view={view} act={act} onClose={() => setAdding(false)} />}

      <div className="colhead" aria-hidden="true">
        <span>Init</span>
        <span />
        <span>Name</span>
        <span>AC</span>
        <span>HP</span>
        <span>Last turn</span>
        {dm && <span />}
      </div>
      <ol className="rows">
        {view.combatants.map((c) => (
          <TrackerRow
            key={c.id}
            c={c}
            dm={dm}
            act={act}
            menuOpen={menuFor === c.id}
            onMenu={() => setMenuFor(menuFor === c.id ? null : c.id)}
            onLog={() => {
              setLogFor(c.id)
              setMenuFor(null)
            }}
          >
            {logOpen && logFor === c.id && <LogPanel attacker={c} view={view} act={act} onClose={() => setLogFor(null)} />}
          </TrackerRow>
        ))}
      </ol>

      {dm && <TurnLog view={view} act={act} />}
    </section>
  )
}

function TrackerRow({
  c,
  dm,
  act,
  menuOpen,
  onMenu,
  onLog,
  children,
}: {
  c: CombatantView
  dm: boolean
  act: Act
  menuOpen: boolean
  onMenu: () => void
  onLog: () => void
  children?: ReactNode
}) {
  const side = c.side === 'ENEMY' ? 'enemy' : 'party'
  const classes = ['item', side, c.isCurrent && 'current', c.defeated && 'defeated', c.hidden && 'hidden']
  const edit = (patch: Record<string, unknown>) => act({ type: 'EditCombatant', id: c.id, ...patch })
  const editInt = (field: string) => (text: string) => {
    const n = Number(text)
    if (Number.isInteger(n)) edit({ [field]: n })
  }
  const myTurn = !dm && c.controlledByViewer && c.isCurrent

  return (
    <li className={classes.filter(Boolean).join(' ')} aria-current={c.isCurrent ? 'step' : undefined}>
      <div className="row">
        <span className="init num" title="Initiative">
          <Editable enabled={dm} display={c.initiative} initial="" label={`initiative for ${c.name}`} onCommit={editInt('initiative')} />
        </span>
        <Portrait c={c} />
        <span className="whobox">
          <span className="name">
            <Editable enabled={dm} display={c.name} initial={c.name} label={`name for ${c.name}`} onCommit={(t) => edit({ name: t })} />
          </span>
          <span className="tags">
            {c.controlledByViewer && <span className="tag you">You</span>}
            {c.isCurrent && <span className="tag turn">{c.controlledByViewer ? 'Your turn' : 'Turn'}</span>}
            {c.hidden && <span className="tag">Hidden</span>}
            {c.defeated && <span className="tag">Defeated</span>}
            {myTurn && !children && (
              <>
                <button type="button" className="btn small primary" onClick={onLog}>
                  Log attack
                </button>
                <button type="button" className="btn small" onClick={() => act({ type: 'NextTurn' })}>
                  End turn ▶
                </button>
              </>
            )}
          </span>
        </span>
        <span className="ac num" title="Armor Class">
          {c.ac === null ? (
            <span className="muted">—</span>
          ) : (
            <Editable enabled={dm} display={c.ac} initial="" label={`AC for ${c.name}`} onCommit={editInt('ac')} />
          )}
        </span>
        <Hp c={c} dm={dm} edit={edit} />
        <span className="last">
          {c.lastTurn ? (
            <>
              <span className="round-tag">Round {c.lastTurn.round}</span>
              {c.lastTurn.attacks.map((a) => (
                <span key={a.id} className={a.hit ? 'hit' : 'miss'}>
                  {describeAttack(a)}
                </span>
              ))}
              {totalLine(c.lastTurn) && <span className="total">{totalLine(c.lastTurn)}</span>}
            </>
          ) : (
            <span className="muted">—</span>
          )}
        </span>
        {dm && (
          <button type="button" className="menu-btn" aria-expanded={menuOpen} aria-label={`Actions for ${c.name}`} onClick={onMenu}>
            ⋯
          </button>
        )}
      </div>
      {dm && menuOpen && (
        <div className="strip">
          <button type="button" className="btn small" onClick={onLog}>
            Log attack
          </button>
          {c.hidden && (
            <button type="button" className="btn small" onClick={() => act({ type: 'RevealCombatant', id: c.id })}>
              Reveal to players
            </button>
          )}
          <button type="button" className="btn small" onClick={() => act({ type: 'SetDefeated', id: c.id, defeated: !c.defeated })}>
            {c.defeated ? 'Restore' : 'Mark defeated'}
          </button>
          <button type="button" className="btn small danger" onClick={() => act({ type: 'RemoveCombatant', id: c.id })}>
            Remove
          </button>
        </div>
      )}
      {children}
    </li>
  )
}

function Hp({ c, dm, edit }: { c: CombatantView; dm: boolean; edit: (patch: Record<string, unknown>) => void }) {
  if (c.hp === null || c.maxHp === null) return <span className="hp num muted">—</span>
  const { hp, maxHp } = c
  const pct = Math.max(0, Math.min(100, Math.round((hp / maxHp) * 100)))
  const level = pct > 50 ? 'high' : pct > 25 ? 'mid' : 'low'
  return (
    <span className="hp">
      <span className="hpnum num">
        <Editable
          enabled={dm}
          display={hp}
          initial=""
          label={`HP for ${c.name}`}
          placeholder="-5, +3, =20"
          onCommit={(t) => {
            const next = applyHpEdit(t, hp, maxHp)
            if (next !== null) edit({ hp: next })
          }}
        />
        <span className="muted">
          /
          <Editable
            enabled={dm}
            display={maxHp}
            initial=""
            label={`max HP for ${c.name}`}
            onCommit={(t) => {
              const n = Number(t)
              if (Number.isInteger(n) && n > 0) edit({ maxHp: n, hp: Math.min(hp, n) })
            }}
          />
        </span>
      </span>
      <span className="bar">
        <i className={level} style={{ width: `${pct}%` }} />
      </span>
    </span>
  )
}
