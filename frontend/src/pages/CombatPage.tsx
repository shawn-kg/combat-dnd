import { useParams, useSearchParams } from 'react-router-dom'
import { Tracker } from '../combat/Tracker'
import { useEncounter } from '../combat/useEncounter'

export function CombatPage() {
  const { encounterId = '' } = useParams()
  const [search] = useSearchParams()
  const { view, connected, send, undo } = useEncounter(encounterId, search.get('as'))

  if (!view) {
    return (
      <main className="page">
        <p className="muted">{connected ? 'Loading the table…' : 'Connecting to the table…'}</p>
      </main>
    )
  }
  return (
    <main className="page">
      <Tracker view={view} connected={connected} send={send} undo={undo} />
    </main>
  )
}
