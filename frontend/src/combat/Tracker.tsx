import type { CombatantView, EncounterView } from '../api/types'
import { describeAttack, initials, totalLine } from './lastTurn'
import './tracker.css'

/** The initiative tracker: one row per combatant, in initiative order, like D&D Beyond's. */
export function Tracker({ view }: { view: EncounterView }) {
  return (
    <section className="tracker" aria-label="Initiative order">
      <div className="tracker-head">
        <span className="round">Round {view.round}</span>
      </div>
      <ol className="rows">
        {view.combatants.map((c) => (
          <TrackerRow key={c.id} c={c} />
        ))}
      </ol>
    </section>
  )
}

function TrackerRow({ c }: { c: CombatantView }) {
  const side = c.side === 'ENEMY' ? 'enemy' : 'party'
  const classes = ['row', side, c.isCurrent && 'current', c.defeated && 'defeated', c.hidden && 'hidden']
  return (
    <li className={classes.filter(Boolean).join(' ')} aria-current={c.isCurrent ? 'step' : undefined}>
      <span className="init num" title="Initiative">
        {c.initiative}
      </span>
      <Portrait c={c} />
      <span className="who">
        <span className="name">{c.name}</span>
        <span className="tags">
          {c.controlledByViewer && <span className="tag you">You</span>}
          {c.isCurrent && <span className="tag turn">Turn</span>}
          {c.hidden && <span className="tag">Hidden</span>}
        </span>
      </span>
      <span className="ac num" title="Armor Class">
        {c.ac ?? '—'}
      </span>
      <Hp hp={c.hp} max={c.maxHp} />
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
    </li>
  )
}

function Portrait({ c }: { c: CombatantView }) {
  return c.portraitKey ? (
    <img className="portrait" src={`/api/portraits/${encodeURIComponent(c.portraitKey)}`} alt="" />
  ) : (
    <span className="portrait initials" aria-hidden="true">
      {initials(c.name)}
    </span>
  )
}

function Hp({ hp, max }: { hp: number | null; max: number | null }) {
  if (hp === null || max === null) {
    return <span className="hp num muted">—</span>
  }
  const pct = Math.max(0, Math.min(100, Math.round((hp / max) * 100)))
  const level = pct > 50 ? 'high' : pct > 25 ? 'mid' : 'low'
  return (
    <span className="hp">
      <span className="num">
        {hp}
        <span className="muted">/{max}</span>
      </span>
      <span className="bar">
        <i className={level} style={{ width: `${pct}%` }} />
      </span>
    </span>
  )
}
