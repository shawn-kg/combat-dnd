import { useCallback, useEffect, useRef, useState } from 'react'
import { WAKING_UP } from '../api/client'
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
  // Why the tracker can't be shown at all (not signed in, no such fight...), learned when the stream fails
  // before the first snapshot. EventSource hides the status code, so ask the state endpoint for the reason.
  const [problem, setProblem] = useState<{ status: number; message: string } | null>(null)
  const gotSnapshot = useRef(false)
  // The newest version we know of, from a snapshot or a command's reply. A ref, so two commands in a row
  // (log an attack, then end the turn) don't send a stale version before the next snapshot arrives.
  const version = useRef(0)

  useEffect(() => {
    const source = new EventSource(`/api/encounters/${encounterId}/stream${asParam}`, { withCredentials: true })
    source.addEventListener('state', (e) => {
      const next: EncounterView = JSON.parse((e as MessageEvent<string>).data)
      version.current = Math.max(version.current, next.version)
      gotSnapshot.current = true
      setProblem(null)
      setView((prev) => (prev && prev.version > next.version ? prev : next))
    })
    source.onopen = () => setConnected(true)
    source.onerror = () => {
      setConnected(false)
      if (gotSnapshot.current) return // a dropped connection: EventSource reconnects by itself
      fetch(`/api/encounters/${encounterId}/state${asParam}`, { credentials: 'include' })
        .then(async (res) => {
          if (res.ok) return
          const body = await res.json().catch(() => null)
          const message =
            body?.message ?? (res.status === 502 || res.status === 503 || res.status === 504 ? WAKING_UP : 'This fight can’t be opened.')
          setProblem({ status: res.status, message })
          // Signed out, not in the campaign, or no such fight: retrying won't help, so stop.
          if (res.status === 401 || res.status === 403 || res.status === 404) source.close()
        })
        .catch(() => undefined) // offline: keep showing "Connecting…" while EventSource retries
    }
    return () => source.close()
  }, [encounterId, asParam])

  // The free host puts the server to sleep after 15 minutes without incoming requests, and the live stream
  // only sends data the other way. While someone has the tracker open and visible, a tiny request every
  // 10 minutes keeps it awake through a quiet stretch of the session. A hidden tab doesn't, so an idle
  // laptop doesn't keep the server up all week.
  useEffect(() => {
    const ping = () => {
      if (document.visibilityState === 'visible') fetch('/actuator/health').catch(() => undefined)
    }
    const timer = window.setInterval(ping, 10 * 60 * 1000)
    return () => window.clearInterval(timer)
  }, [])

  const post = useCallback(
    async (path: 'commands' | 'undo' | 'begin' | 'end', body?: object): Promise<SendResult> => {
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

  /** DM only: initiative is in, start round 1. */
  const begin = useCallback(() => post('begin'), [post])

  /** DM only: end the fight; party HP is saved to the characters. */
  const end = useCallback(() => post('end'), [post])

  return { view, connected, problem, send, undo, begin, end }
}
