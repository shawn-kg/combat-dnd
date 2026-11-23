import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import type { CampaignView, PrepEnemy, PrepPartyMember, PrepView } from '../api/campaign'
import { api, ApiError } from '../api/client'
import { Portrait } from '../combat/Portrait'
import { Steps } from '../combat/Steps'
import './pages.css'

interface Draft {
  name: string
  party: PrepPartyMember[]
  enemies: PrepEnemy[]
}

const toBody = (d: Draft) => ({
  name: d.name,
  party: d.party.map((p) => ({ characterId: p.characterId, included: p.included })),
  enemies: d.enemies.map((e) => ({ templateId: e.templateId, count: e.count, hidden: e.hidden })),
})

/** The DM sets up one encounter ahead of time: who's playing, which monsters. Saved as you go. */
export function PrepPage() {
  const { campaignId = '', encounterId = '' } = useParams()
  const prep = useQuery({ queryKey: ['prep', encounterId], queryFn: () => api<PrepView>('GET', `/encounters/${encounterId}/prep`), retry: false })
  const campaign = useQuery({ queryKey: ['campaign', campaignId], queryFn: () => api<CampaignView>('GET', `/campaigns/${campaignId}`) })

  if (prep.isPending || campaign.isPending) return <main className="page muted">Loading…</main>
  const error = (prep.error ?? campaign.error) as ApiError | null
  if (error) {
    return (
      <main className="page stack">
        <div className="notice">{error.message}</div>
        <Link to={`/c/${campaignId}`}>Back to the campaign</Link>
      </main>
    )
  }
  if (!prep.data || !campaign.data) return null
  if (prep.data.status !== 'PREPARING') return <Navigate to={`/combat/${encounterId}`} replace />
  return <PrepForm key={encounterId} initial={prep.data} campaign={campaign.data} />
}

function PrepForm({ initial, campaign }: { initial: PrepView; campaign: CampaignView }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [draft, setDraftState] = useState<Draft>({ name: initial.name, party: initial.party, enemies: initial.enemies })
  const [saved, setSaved] = useState<'saved' | 'saving' | 'error'>('saved')
  const [error, setError] = useState<string | null>(null)
  const first = useRef(true)
  const pending = useRef<Promise<unknown>>(Promise.resolve())

  const setDraft = (change: (d: Draft) => Draft) => {
    setDraftState(change)
    setSaved('saving')
  }

  // Save a moment after each change, so typing the name doesn't send a request per key.
  useEffect(() => {
    if (first.current) {
      first.current = false
      return
    }
    if (!draft.name.trim()) return
    const t = window.setTimeout(() => {
      pending.current = api('PUT', `/encounters/${initial.id}/prep`, toBody(draft)).then(
        () => {
          setSaved('saved')
          setError(null)
          qc.invalidateQueries({ queryKey: ['campaign', initial.campaignId] })
        },
        (e: ApiError) => {
          setSaved('error')
          setError(e.message)
        },
      )
    }, 400)
    return () => window.clearTimeout(t)
  }, [draft, initial.id, initial.campaignId, qc])

  const start = useMutation({
    mutationFn: async () => {
      // Let an autosave that's already on its way land first, so it can't overwrite the final save.
      await pending.current.catch(() => undefined)
      await api('PUT', `/encounters/${initial.id}/prep`, toBody(draft))
      return api('POST', `/encounters/${initial.id}/start`)
    },
    onSuccess: () => navigate(`/combat/${initial.id}`),
    onError: (e: ApiError) => setError(e.message),
  })

  const setParty = (id: string, included: boolean) =>
    setDraft((d) => ({ ...d, party: d.party.map((p) => (p.characterId === id ? { ...p, included } : p)) }))
  const setEnemy = (i: number, patch: Partial<PrepEnemy>) =>
    setDraft((d) => ({ ...d, enemies: d.enemies.map((e, j) => (j === i ? { ...e, ...patch } : e)) }))
  const removeEnemy = (i: number) => setDraft((d) => ({ ...d, enemies: d.enemies.filter((_, j) => j !== i) }))
  const addEnemy = (templateId: string) =>
    setDraft((d) => {
      const i = d.enemies.findIndex((e) => e.templateId === templateId)
      if (i >= 0) return { ...d, enemies: d.enemies.map((e, j) => (j === i ? { ...e, count: Math.min(20, e.count + 1) } : e)) }
      const t = campaign.templates.find((x) => x.id === templateId)!
      return { ...d, enemies: [...d.enemies, { templateId, name: t.name, portraitKey: t.portraitKey, ac: t.ac, maxHp: t.maxHp, count: 1, hidden: false }] }
    })

  const inFight = draft.party.filter((p) => p.included).length + draft.enemies.reduce((n, e) => n + e.count, 0)

  return (
    <main className="page stack">
      <div className="lrow">
        <Link to={`/c/${initial.campaignId}`} className="small">
          ◀ {campaign.name}
        </Link>
        <span className="spacer" />
        <Steps on="prep" />
      </div>

      <section className="card">
        <h3>Encounter</h3>
        <div className="lrow">
          <span className="lbl">Name</span>
          <input
            className="field w"
            aria-label="Encounter name"
            value={draft.name}
            onChange={(e) => setDraft((d) => ({ ...d, name: e.target.value }))}
          />
          <span className={`small ${saved === 'error' ? 'err-text' : 'ok'}`} aria-live="polite">
            {saved === 'saved' ? '✓ Saved' : saved === 'saving' ? 'Saving…' : 'Not saved'}
          </span>
        </div>
      </section>

      <section className="card">
        <h3>Party</h3>
        <p className="muted small">
          Untick anyone who isn’t playing tonight. Stats and HP are on the <Link to={`/c/${initial.campaignId}`}>campaign page</Link>, and
          players can set their own HP there.
        </p>
        {draft.party.length === 0 && <p className="muted small">No characters yet. Add them on the campaign page.</p>}
        <div className="list">
          {draft.party.map((p) => (
            <label key={p.characterId} className={`list-row prep-row${p.included ? '' : ' off'}`}>
              <input type="checkbox" checked={p.included} onChange={(e) => setParty(p.characterId, e.target.checked)} aria-label={`${p.name} is in this fight`} />
              <Portrait c={p} size="mini" />
              <span className="grow">
                <b>{p.name}</b> <span className="muted small">{p.playerName ?? 'Not claimed yet'}</span>
              </span>
              <span className="muted small num">
                AC {p.ac} · {p.hp}/{p.maxHp} HP
              </span>
            </label>
          ))}
        </div>
      </section>

      <section className="card">
        <h3>Enemies</h3>
        {draft.enemies.length === 0 && <p className="muted small">No enemies yet. Add some from your monsters below.</p>}
        <div className="list">
          {draft.enemies.map((e, i) => (
            <div key={e.templateId + i} className="list-row prep-row enemy">
              <Portrait c={e} size="mini" />
              <span className="grow">
                <b>{e.name}</b>{' '}
                <span className="muted small num">
                  AC {e.ac} · {e.maxHp} HP each
                </span>
              </span>
              <span className="stepper">
                <button type="button" aria-label={`One fewer ${e.name}`} disabled={e.count <= 1} onClick={() => setEnemy(i, { count: e.count - 1 })}>
                  −
                </button>
                <b className="num">{e.count}</b>
                <button type="button" aria-label={`One more ${e.name}`} disabled={e.count >= 20} onClick={() => setEnemy(i, { count: e.count + 1 })}>
                  +
                </button>
              </span>
              <label className="small">
                <input type="checkbox" checked={e.hidden} onChange={(ev) => setEnemy(i, { hidden: ev.target.checked })} /> Hidden
              </label>
              <button type="button" className="btn small danger" aria-label={`Remove ${e.name}`} onClick={() => removeEnemy(i)}>
                ✕
              </button>
            </div>
          ))}
        </div>
        <div className="lrow add-row">
          <span className="lbl">Add</span>
          {campaign.templates.length === 0 && (
            <span className="muted small">
              No monsters yet. Make some on the <Link to={`/c/${initial.campaignId}`}>campaign page</Link>.
            </span>
          )}
          {campaign.templates.map((t) => (
            <button type="button" key={t.id} className="chip with-pic" onClick={() => addEnemy(t.id)}>
              <Portrait c={t} size="mini" />+ {t.name}
            </button>
          ))}
        </div>
      </section>

      {error && <div className="err">{error}</div>}
      <div className="lrow">
        <span className="muted small">Hidden enemies are on your tracker but players don’t see them until you reveal them.</span>
        <span className="spacer" />
        <button className="btn primary" disabled={inFight === 0 || start.isPending} onClick={() => start.mutate()}>
          Start combat: roll initiative ▶
        </button>
      </div>
    </main>
  )
}
