import { useCallback, useEffect, useRef, useState } from 'react'
import type { Command, EncounterView } from '../api/types'

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
  // The newest version we know of, from a snapshot or a command's reply. A ref, so two commands in a row
  // (log an attack, then end the turn) don't send a stale version before the next snapshot arrives.
  const version = useRef(0)

  useEffect(() => {
    const source = new EventSource(`/api/encounters/${encounterId}/stream${asParam}`, { withCredentials: true })
    source.addEventListener('state', (e) => {
      const next: EncounterView = JSON.parse((e as MessageEvent<string>).data)
      version.current = Math.max(version.current, next.version)
      setView((prev) => (prev && prev.version > next.version ? prev : next))
    })
    source.onopen = () => setConnected(true)
    source.onerror = () => setConnected(false)
    return () => source.close()
  }, [encounterId, asParam])

  const post = useCallback(
    async (path: 'commands' | 'undo', body?: object): Promise<SendResult> => {
      try {
        const res = await fetch(`/api/encounters/${encounterId}/${path}${asParam}`, {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'combat-dnd' },
          body: body ? JSON.stringify(body) : undefined,
        })
        if (res.ok) {
          const reply = await res.json().catch(() => null)
          if (typeof reply?.version === 'number') version.current = Math.max(version.current, reply.version)
          return { ok: true }
        }
        if (res.status === 409) return { ok: false, message: 'The table changed. Check and try again.' }
        const err = await res.json().catch(() => null)
        return { ok: false, message: err?.message ?? 'Something went wrong. Try again.' }
      } catch {
        return { ok: false, message: "Can't reach the server. Check your connection." }
      }
    },
    [encounterId, asParam],
  )

  /** Sends a command with the newest version we know of. No optimistic updates: the next snapshot shows the result. */
  const send = useCallback((command: Command) => post('commands', { ...command, expectedVersion: version.current }), [post])

  /** DM only: reverses the last command. */
  const undo = useCallback(() => post('undo'), [post])

  return { view, connected, send, undo }
}
