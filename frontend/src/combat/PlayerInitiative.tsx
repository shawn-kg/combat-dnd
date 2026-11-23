import { useState } from 'react'
import type { Command, EncounterView } from '../api/types'
import { parseInitiative } from './initiative'
import { Portrait } from './Portrait'
import type { SendResult } from './useEncounter'

/**
 * A player during the initiative step: type your roll and send it to the DM. You can change it until
 * round 1 begins. 0 is the tracker's "not rolled yet", so a roll of 0 shows as waiting (the DM can still enter it).
 */
export function PlayerInitiative({ view, send }: { view: EncounterView; send: (command: Command) => Promise<SendResult> }) {
  const mine = view.combatants.filter((c) => c.controlledByViewer)
  return (
    <div className="stack">
      <div className="wait-card">
        <b>Rolling initiative…</b>
        {mine.length > 0
          ? 'Roll for initiative and send it to the DM. The tracker appears here when round 1 begins.'
          : `Roll for ${view.name} and tell the DM. The tracker appears here when round 1 begins.`}
      </div>
      {mine.map((c) => (
        <MyRoll key={c.id} id={c.id} name={c.name} portraitKey={c.portraitKey} sent={c.initiative} send={send} />
      ))}
    </div>
  )
}

function MyRoll({
  id,
  name,
  portraitKey,
  sent,
  send,
}: {
  id: string
  name: string
  portraitKey: string | null
  sent: number
  send: (command: Command) => Promise<SendResult>
}) {
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit() {
    const n = parseInitiative(text)
    if (n === null) return setError('Type your roll as a whole number.')
    setBusy(true)
    const r = await send({ type: 'RollInitiative', id, initiative: n })
    setBusy(false)
    if (r.ok) {
      setText('')
      setError(null)
    } else setError(r.message)
  }

  return (
    <section className="card mine">
      <form
        className="lrow"
        onSubmit={(e) => {
          e.preventDefault()
          submit()
        }}
      >
        <Portrait c={{ name, portraitKey }} />
        <span className="grow">
          <b>{name}</b> <span className="tag you">You</span>
          <br />
          <span className="small muted">{sent !== 0 ? `✓ The DM has your roll: ${sent}. You can change it until round 1.` : 'Not sent yet.'}</span>
        </span>
        <input
          className="field n"
          type="number"
          inputMode="numeric"
          aria-label={`Initiative for ${name}`}
          placeholder={sent !== 0 ? String(sent) : 'Roll'}
          value={text}
          onChange={(e) => setText(e.target.value)}
          autoFocus
        />
        <button className="btn primary" disabled={busy || text.trim() === ''}>
          {sent !== 0 ? 'Change' : 'Send'}
        </button>
      </form>
      {error && <div className="err">{error}</div>}
    </section>
  )
}
