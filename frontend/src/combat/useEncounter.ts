import { useCallback, useEffect, useState } from 'react'
import type { EncounterView } from '../api/types'

export type SendResult = { ok: true } | { ok: false; message: string }

/**
 * Live encounter state. The server pushes this viewer's full filtered snapshot over SSE after every change;
 * the screen renders purely from the latest one. EventSource reconnects on its own and gets a fresh snapshot.
 */
export function useEncounter(encounterId: string, devActor?: string | null) {
  // Development only: ?as=dm or ?as=player:<id> picks who you are until joining exists (milestone 3).
  const asParam = devActor ? `?as=${encodeURIComponent(devActor)}` : ''
  const [view, setView] = useState<EncounterView | null>(null)
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    const source = new EventSource(`/api/encounters/${encounterId}/stream${asParam}`, { withCredentials: true })
    source.addEventListener('state', (e) => setView(JSON.parse((e as MessageEvent<string>).data)))
    source.onopen = () => setConnected(true)
    source.onerror = () => setConnected(false)
    return () => source.close()
  }, [encounterId, asParam])

  /** Sends a combat command with the version we last saw. No optimistic updates: the next snapshot shows the result. */
  const send = useCallback(
    async (command: { type: string } & Record<string, unknown>): Promise<SendResult> => {
      const res = await fetch(`/api/encounters/${encounterId}/commands${asParam}`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'combat-dnd' },
        body: JSON.stringify({ ...command, expectedVersion: view?.version }),
      })
      if (res.ok) return { ok: true }
      if (res.status === 409) return { ok: false, message: 'The table changed. Check and try again.' }
      const body = await res.json().catch(() => null)
      return { ok: false, message: body?.message ?? 'Something went wrong.' }
    },
    [encounterId, asParam, view?.version],
  )

  return { view, connected, send }
}
