import { useParams } from 'react-router-dom'
import { Tracker } from '../combat/Tracker'
import { useEncounter } from '../combat/useEncounter'

export function CombatPage() {
  const { encounterId = '' } = useParams()
  const { view, connected } = useEncounter(encounterId)

  if (!view) {
    return (
      <main className="page">
        <p className="muted">{connected ? 'Loading the table…' : 'Connecting to the table…'}</p>
      </main>
    )
  }
  return (
    <main className="page">
      <Tracker view={view} />
    </main>
  )
}
