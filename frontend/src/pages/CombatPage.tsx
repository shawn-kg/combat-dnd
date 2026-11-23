import { Link, useParams, useSearchParams } from 'react-router-dom'
import { Initiative } from '../combat/Initiative'
import { PlayerInitiative } from '../combat/PlayerInitiative'
import { Tracker } from '../combat/Tracker'
import { useEncounter } from '../combat/useEncounter'

/** One fight, whatever stage it's at: still being set up, rolling initiative, under way, or over. */
export function CombatPage() {
  const { encounterId = '' } = useParams()
  const [search] = useSearchParams()
  const { view, connected, problem, send, undo, begin, end } = useEncounter(encounterId, search.get('as'))

  if (!view) {
    return (
      <main className="page stack">
        {problem ? (
          <>
            <div className="notice" role="alert">
              {problem.message}
            </div>
            {problem.status === 401 && <p className="muted small">If you’re a player, open the join link your DM sent you, then come back to the fight.</p>}
            <Link to="/">Go to the start</Link>
          </>
        ) : (
          <p className="muted">{connected ? 'Loading the table…' : 'Connecting to the table…'}</p>
        )}
      </main>
    )
  }
  const back = (
    <Link to={`/c/${view.campaignId}`} className="small">
      ◀ Back to the campaign
    </Link>
  )

  if (view.status === 'PREPARING') {
    return (
      <main className="page stack">
        {back}
        <div className="wait-card">
          <b>The DM is setting up {view.name}</b>
          The tracker appears here when combat starts.
          {view.viewerIsDm && (
            <p>
              <Link to={`/c/${view.campaignId}/encounters/${encounterId}`}>Open the setup</Link>
            </p>
          )}
        </div>
      </main>
    )
  }

  if (view.status === 'ENDED') {
    return (
      <main className="page stack">
        {back}
        <div className="wait-card">
          <b>{view.name} is over</b>
          It lasted {view.round} {view.round === 1 ? 'round' : 'rounds'}. Everyone’s HP has been saved to their character for next time.
        </div>
      </main>
    )
  }

  if (view.status === 'ROLLING_INITIATIVE') {
    return (
      <main className="page stack">
        {back}
        {view.viewerIsDm ? (
          <Initiative view={view} send={send} begin={begin} />
        ) : (
          <PlayerInitiative view={view} send={send} />
        )}
      </main>
    )
  }

  return (
    <main className="page">
      <Tracker view={view} connected={connected} send={send} undo={undo} end={end} />
    </main>
  )
}
