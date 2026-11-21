import type { CombatantView, EncounterView } from '../api/types'
import './combat.css'

/** The chat-style initiative list: enemies on the left, the party on the right, in initiative order. */
export function InitiativeFeed({ view }: { view: EncounterView }) {
  return (
    <section className="feed" aria-label="Initiative order">
      <div className="round-div">ROUND {view.round}</div>
      {view.combatants.map((c) => (
        <div key={c.id} className={`row ${c.side === 'ENEMY' ? 'enemy' : 'party'}`}>
          <Bubble c={c} />
        </div>
      ))}
    </section>
  )
}

function Bubble({ c }: { c: CombatantView }) {
  const classes = ['bubble', c.side === 'ENEMY' ? 'enemy' : 'party', c.isCurrent && 'current', c.defeated && 'defeated']
  return (
    <article className={classes.filter(Boolean).join(' ')}>
      <div className="b-head">
        <span className="init num" title="Initiative">
          {c.initiative}
        </span>
        <span className="name">{c.name}</span>
        {c.controlledByViewer && <span className="tag you">You</span>}
        {c.isCurrent && <span className="tag turn">{c.controlledByViewer ? 'Your turn' : 'Turn'}</span>}
        {c.hidden && <span className="tag">Hidden</span>}
      </div>
      {c.hp !== null && c.maxHp !== null && <HpBar hp={c.hp} max={c.maxHp} ac={c.ac} />}
      {c.conditions.length > 0 && (
        <div className="conds">
          {c.conditions.map((cond) => (
            <span key={cond.name} className={`cond${cond.private ? ' priv' : ''}`}>
              {cond.name}
            </span>
          ))}
        </div>
      )}
    </article>
  )
}

function HpBar({ hp, max, ac }: { hp: number; max: number; ac: number | null }) {
  const pct = Math.max(0, Math.min(100, Math.round((hp / max) * 100)))
  const level = pct > 50 ? 'high' : pct > 25 ? 'mid' : 'low'
  return (
    <div className="stats">
      <div className="hpbar">
        <i className={level} style={{ width: `${pct}%` }} />
      </div>
      <span className="num hpnum">
        {hp}
        <span className="muted">/{max}</span>
      </span>
      {ac !== null && <span className="num ac">AC {ac}</span>}
    </div>
  )
}
