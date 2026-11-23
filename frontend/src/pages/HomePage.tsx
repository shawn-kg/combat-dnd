import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import type { MyCampaign } from '../api/campaign'
import { api, ApiError } from '../api/client'
import './pages.css'

/** Your campaigns, and starting a new one. Players arrive through a join link instead. */
export function HomePage() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const mine = useQuery({ queryKey: ['my-campaigns'], queryFn: () => api<MyCampaign[]>('GET', '/me/campaigns') })
  const create = useMutation({
    mutationFn: () => api<{ id: string }>('POST', '/campaigns', { name }),
    onSuccess: (c) => navigate(`/c/${c.id}`),
  })

  return (
    <main className="page stack">
      <h1 className="title">Combat D&amp;D</h1>
      <p className="muted lead">A live initiative tracker for your table. The DM runs it; players join with a link.</p>

      {mine.data && mine.data.length > 0 && (
        <section className="card">
          <h3>Your campaigns</h3>
          <ul className="plain">
            {mine.data.map((c) => (
              <li key={`${c.id}-${c.role}`}>
                <Link to={`/c/${c.id}`}>{c.name}</Link> <span className="tag">{c.role === 'DM' ? 'DM' : 'Player'}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="card">
        <h3>Start a campaign</h3>
        <p className="muted small">You'll be its DM. You get a join link to send your players.</p>
        <form
          className="lrow"
          onSubmit={(e) => {
            e.preventDefault()
            if (name.trim()) create.mutate()
          }}
        >
          <input className="field grow" aria-label="Campaign name" placeholder="e.g. Tuesday Group" value={name} onChange={(e) => setName(e.target.value)} />
          <button className="btn primary" disabled={!name.trim() || create.isPending}>
            Start campaign
          </button>
        </form>
        {create.error && <div className="err">{(create.error as ApiError).message}</div>}
      </section>

      <p className="muted small">Joining a game? Open the link your DM sent you.</p>
    </main>
  )
}
