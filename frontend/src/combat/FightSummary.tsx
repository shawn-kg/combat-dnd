import type { EncounterView } from '../api/types'
import { summaryRows, transcriptUrl } from './fightStats'
import { Portrait } from './Portrait'

/** The end-of-fight table: who dealt and took what. Players see it under the same rules as the tracker. */
export function FightSummary({ view, encounterId }: { view: EncounterView; encounterId: string }) {
  const rows = summaryRows(view.combatants)
  const top = rows.find((c) => c.totals.dealt > 0)
  return (
    <section className="card" aria-label="Fight summary">
      <h3>Summary</h3>
      <p className="muted small">
        {view.round} {view.round === 1 ? 'round' : 'rounds'}
        {top && (
          <>
            {' '}
            · Most damage dealt: <b>{top.name}</b> ({top.totals.dealt})
          </>
        )}
        . Damage counts as logged.
      </p>
      <div className="table-scroll">
        <table className="summary">
          <thead>
            <tr>
              <th scope="col">Combatant</th>
              <th scope="col" className="n">Dealt</th>
              <th scope="col" className="n">Taken</th>
              <th scope="col" className="n">Hits</th>
              <th scope="col" className="n">Misses</th>
              <th scope="col" className="n">Biggest</th>
              <th scope="col">End</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((c) => (
              <tr key={c.id} className={c.side.toLowerCase()}>
                <th scope="row">
                  <span className="who">
                    <Portrait c={c} size="mini" /> {c.name}
                  </span>
                </th>
                <td className="n num">{c.totals.dealt}</td>
                <td className="n num">{c.totals.taken}</td>
                <td className="n num">{c.totals.hits}</td>
                <td className="n num">{c.totals.misses}</td>
                <td className="n num">{c.totals.biggestHit || '—'}</td>
                <td className="num">
                  {c.defeated ? 'Defeated' : c.hp === null ? '—' : c.hp === 0 ? `Down (0/${c.maxHp})` : `${c.hp}/${c.maxHp}`}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {view.viewerIsDm && <TranscriptLinks encounterId={encounterId} />}
    </section>
  )
}

/** DM only: download the fight as Markdown (to read or paste) or CSV (one row per attack). */
export function TranscriptLinks({ encounterId, compact = false }: { encounterId: string; compact?: boolean }) {
  return (
    <span className="transcript-links">
      <a className="btn small" href={transcriptUrl(encounterId, 'md')} download title="Download the transcript as Markdown, to read or paste into notes or Discord">
        ↓ Transcript{compact ? '' : ' (.md)'}
      </a>
      <a className="btn small" href={transcriptUrl(encounterId, 'csv')} download title="Download every attack as a spreadsheet (CSV)">
        ↓ CSV
      </a>
    </span>
  )
}
