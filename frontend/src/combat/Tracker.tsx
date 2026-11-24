import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import type { CombatantView, Command, EncounterView } from '../api/types'
import { AddEnemy } from './AddEnemy'
import { AddNpc } from './AddNpc'
import { AddPartyMember } from './AddPartyMember'
import { Editable } from './Editable'
import { applyHpEdit } from './hpEdit'
import { describeAttack, totalLine } from './lastTurn'
import { LogPanel } from './LogPanel'
import { Portrait } from './Portrait'
import { TurnLog } from './TurnLog'
import type { SendResult } from './useEncounter'
import './tracker.css'

/**
 * Opens this page in its own narrow window, sized to sit beside D&D Beyond. The same name each time,
 * so pressing it again brings that window back instead of opening another.
 */
function popOut(name: string) {
  const w = window.open(window.location.href, `combat-dnd:${window.location.pathname}`, 'popup,width=440,height=900')
  if (w) {
    w.focus()
    w.document.title = `${name} · Combat D&D`
  }
}

/** Sends a command; on failure shows the reason and resolves false. */
export type Act = (command: Command) => Promise<boolean>

/** The initiative tracker: one row per combatant, in initiative order. The DM gets editing and controls. */
export function Tracker({
  view,
  connected,
  send,
  undo,
  end,
}: {
  view: EncounterView
  connected: boolean
  send: (command: Command) => Promise<SendResult>
  undo: () => Promise<SendResult>
  /** DM only: end the fight. */
  end?: () => Promise<SendResult>
}) {
  const [logFor, setLogFor] = useState<string | null>(null)
  const [menuFor, setMenuFor] = useState<string | null>(null)
  const [adding, setAdding] = useState<'enemy' | 'party' | 'npc' | null>(null)
  const [ending, setEnding] = useState(false)
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
  const logOpen = logAttacker && (dm || logAttacker.isCurrent) && logAttacker.hp !== 0 // closes if they drop to 0 HP

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
        <button
          type="button"
          className="btn small wide-only"
          title="Open the tracker in its own small window, to keep beside D&D Beyond"
          onClick={() => popOut(view.name)}
        >
          ⧉ Pop out
        </button>
        {dm && (
          <span className="dm-buttons">
            <button type="button" className="btn small" aria-label="Undo (U)" onClick={doUndo}>
              ↶<span className="wide-only"> Undo</span>
              <kbd className="wide-only">U</kbd>
            </button>
            <button type="button" className="btn small" aria-label="Add" aria-expanded={adding !== null} onClick={() => setAdding(adding ? null : 'enemy')}>
              +<span className="wide-only"> Add</span>
            </button>
            <button type="button" className="btn small" aria-label="Previous turn" onClick={() => act({ type: 'PreviousTurn' })}>
              ◀<span className="wide-only"> Previous</span>
            </button>
            <button type="button" className="btn small primary" aria-label="Next turn (N)" onClick={() => act({ type: 'NextTurn' })}>
              Next turn ▶<kbd className="wide-only">N</kbd>
            </button>
          </span>
        )}
      </div>

      {notice && (
        <div className="notice" role="alert">
          {notice}
        </div>
      )}
      {dm && adding && (
        <>
          <div className="lrow" role="group" aria-label="What to add">
            <button type="button" className="chip" aria-pressed={adding === 'enemy'} onClick={() => setAdding('enemy')}>
              Enemies
            </button>
            <button type="button" className="chip" aria-pressed={adding === 'party'} onClick={() => setAdding('party')}>
              Party member
            </button>
            <button type="button" className="chip" aria-pressed={adding === 'npc'} onClick={() => setAdding('npc')}>
              NPC
            </button>
          </div>
          {adding === 'enemy' && <AddEnemy view={view} act={act} onClose={() => setAdding(null)} />}
          {adding === 'party' && <AddPartyMember view={view} act={act} onClose={() => setAdding(null)} />}
          {adding === 'npc' && <AddNpc view={view} act={act} onClose={() => setAdding(null)} />}
        </>
      )}

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

      {dm && end && !ending && (
        <div className="lrow end-row">
          <span className="spacer" />
          <button type="button" className="btn small danger" onClick={() => setEnding(true)}>
            End combat…
          </button>
        </div>
      )}
      {dm && end && ending && (
        <div className="notice confirm" role="alertdialog" aria-label="End combat">
          <span>End {view.name}? Everyone’s HP is saved to their character, and the tracker closes for everyone.</span>
          <span className="spacer" />
          <button
            type="button"
            className="btn small danger"
            onClick={async () => {
              const r = await end()
              if (!r.ok) show(r.message)
              setEnding(false)
            }}
          >
            End combat
          </button>
          <button type="button" className="btn small" onClick={() => setEnding(false)}>
            Keep fighting
          </button>
        </div>
      )}
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
  const side = c.side === 'ENEMY' ? 'enemy' : c.side === 'NPC' ? 'npc' : 'party'
  const classes = ['item', side, c.isCurrent && 'current', c.defeated && 'defeated', c.hidden && 'hidden']
  const edit = (patch: Record<string, unknown>) => act({ type: 'EditCombatant', id: c.id, ...patch })
  const editInt = (field: string) => (text: string) => {
    const n = Number(text)
    if (Number.isInteger(n)) edit({ [field]: n })
  }
  // On a turn, the person running that combatant gets the buttons right on the row: the player on their
  // own turn, and the DM on every turn (so enemy turns don't need the ⋯ menu).
  const runsThisTurn = c.isCurrent && (dm || c.controlledByViewer)
  // At 0 HP they still take their turn (death saves) but can't attack until healed. Null HP means the viewer
  // can't see it (an enemy, for a player), and players never run enemies, so that never hides their own button.
  const down = c.hp === 0

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
            {runsThisTurn && !children && (
              <>
                {down ? (
                  <span className="down-note">0 HP: can’t attack until healed</span>
                ) : (
                  <button type="button" className="btn small primary" onClick={onLog}>
                    Log attack
                  </button>
                )}
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
        <Hp c={c} dm={dm} canEditHp={dm || c.controlledByViewer} edit={edit} />
        <span className={`last${c.lastTurn ? '' : ' none'}`}>
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
          {!down && (
            <button type="button" className="btn small" onClick={onLog}>
              Log attack
            </button>
          )}
          {c.hidden && (
            <button type="button" className="btn small" onClick={() => act({ type: 'RevealCombatant', id: c.id })}>
              Reveal to players
            </button>
          )}
          {c.side === 'NPC' && (
            <button type="button" className="btn small" onClick={() => edit({ statsVisible: !c.statsVisible })}>
              {c.statsVisible ? 'Hide AC & HP from players' : 'Show AC & HP to players'}
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

function Hp({
  c,
  dm,
  canEditHp,
  edit,
}: {
  c: CombatantView
  dm: boolean
  /** The DM, or a player on their own row: players may set their own HP, but not max HP. */
  canEditHp: boolean
  edit: (patch: Record<string, unknown>) => void
}) {
  if (c.hp === null || c.maxHp === null) return <span className="hp num muted">—</span>
  const { hp, maxHp } = c
  const pct = Math.max(0, Math.min(100, Math.round((hp / maxHp) * 100)))
  const level = pct > 50 ? 'high' : pct > 25 ? 'mid' : 'low'
  return (
    <span className="hp">
      <span className="hpnum num">
        <Editable
          enabled={canEditHp}
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
