import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import type { JoinInfo } from '../api/campaign'
import { api, ApiError } from '../api/client'
import './pages.css'

/** A player opens the DM's link: new players type a name, returning ones pick theirs. Then the DM approves. */
export function JoinPage() {
  const { code = '' } = useParams()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [returningId, setReturningId] = useState('')
  const info = useQuery({ queryKey: ['join', code], queryFn: () => api<JoinInfo>('GET', `/join/${code}`), retry: false })
  const join = useMutation({
    mutationFn: () => api<{ campaignId: string }>('POST', `/join/${code}`, returningId ? { returningPlayerId: returningId } : { name }),
    onSuccess: (r) => navigate(`/c/${r.campaignId}`),
  })

  if (info.isPending) return <main className="page muted">Opening the link…</main>
  if (info.error)
    return (
      <main className="page stack">
        <h1 className="title">Join a campaign</h1>
        <div className="notice">{(info.error as ApiError).message}</div>
      </main>
    )

  const returning = info.data.players
  return (
    <main className="page stack">
      <h1 className="title">Join {info.data.campaignName}</h1>
      <form
        className="card"
        onSubmit={(e) => {
          e.preventDefault()
          join.mutate()
        }}
      >
        <h3>{returningId ? 'Welcome back' : 'Your name'}</h3>
        {!returningId && (
          <input className="field" aria-label="Your name" placeholder="What the table calls you" value={name} onChange={(e) => setName(e.target.value)} autoFocus />
        )}
        {returning.length > 0 && (
          <label className="lrow small">
            <span className="muted">Joined before on another device?</span>
            <select className="field" aria-label="I'm returning as" value={returningId} onChange={(e) => setReturningId(e.target.value)}>
              <option value="">I'm new here</option>
              {returning.map((p) => (
                <option key={p.id} value={p.id}>
                  I'm returning as {p.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <p className="muted small">The DM lets you in. Then you choose your character.</p>
        <div className="lrow">
          <span className="spacer" />
          <button className="btn primary" disabled={join.isPending || (!returningId && !name.trim())}>
            Join
          </button>
        </div>
        {join.error && <div className="err">{(join.error as ApiError).message}</div>}
      </form>
    </main>
  )
}
