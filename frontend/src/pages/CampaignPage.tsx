import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import type { CampaignView, CharacterDto, EncounterSummary, TemplateDto } from '../api/campaign'
import type { EncounterStatus } from '../api/types'
import { api, ApiError } from '../api/client'
import { applyHpEdit } from '../combat/hpEdit'
import { Portrait } from '../combat/Portrait'
import { PortraitPicker } from '../combat/PortraitPicker'
import './pages.css'

/** A campaign's home: the DM manages players and characters; a player waits, chooses a character, sets their HP. */
export function CampaignPage() {
  const { campaignId = '' } = useParams()
  // Refetched every few seconds so the lobby and the DM's join requests update without reloading.
  const campaign = useQuery({
    queryKey: ['campaign', campaignId],
    queryFn: () => api<CampaignView>('GET', `/campaigns/${campaignId}`),
    refetchInterval: 3000,
    retry: false,
  })

  if (campaign.isPending) return <main className="page muted">Loading…</main>
  if (campaign.error) {
    const e = campaign.error as ApiError
    return (
      <main className="page stack">
        <div className="notice">{e.message}</div>
        <Link to="/">Back to the start</Link>
      </main>
    )
  }
  const c = campaign.data
  return (
    <main className="page stack">
      <h1 className="title">{c.name}</h1>
      {c.role === 'DM' ? <DmView c={c} /> : <PlayerView c={c} />}
    </main>
  )
}

/** Runs a mutation, then refreshes the campaign. Errors come back as `error` for inline display. */
function useCampaignAction<V, R = unknown>(campaignId: string, fn: (v: V) => Promise<R>) {
  const qc = useQueryClient()
  return useMutation({ mutationFn: fn, onSuccess: () => qc.invalidateQueries({ queryKey: ['campaign', campaignId] }) })
}

// ======================= DM =======================

function DmView({ c }: { c: CampaignView }) {
  return (
    <>
      <Encounters c={c} />
      <JoinLink c={c} />
      <Players c={c} />
      <Characters c={c} />
      <Monsters c={c} />
    </>
  )
}

const STATUS_LABEL: Record<EncounterStatus, string> = {
  PREPARING: 'Setting up',
  ROLLING_INITIATIVE: 'Rolling initiative',
  ACTIVE: 'In combat',
  ENDED: 'Over',
}

/** Where an encounter opens: its prep page while it's being set up, the tracker after that. */
function encounterLink(campaignId: string, e: EncounterSummary) {
  return e.status === 'PREPARING' ? `/c/${campaignId}/encounters/${e.id}` : `/combat/${e.id}`
}

function Encounters({ c }: { c: CampaignView }) {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [deleting, setDeleting] = useState<string | null>(null)
  const create = useCampaignAction(c.id, (n: string) => api<{ id: string }>('POST', `/campaigns/${c.id}/encounters`, { name: n }))
  const remove = useCampaignAction(c.id, (id: string) => api('DELETE', `/encounters/${id}`))
  const error = create.error ?? remove.error

  return (
    <section className="card">
      <h3>Encounters</h3>
      <p className="muted small">Set fights up before the session, then start one when the party walks into it.</p>
      {c.encounters.length > 0 && (
        <div className="list">
          {c.encounters.map((e) => (
            <div key={e.id} className={`list-row${e.status === 'ACTIVE' || e.status === 'ROLLING_INITIATIVE' ? ' live' : ''}`}>
              <Link to={encounterLink(c.id, e)}>
                <b>{e.name}</b>
              </Link>
              <span className={`tag status-${e.status.toLowerCase()}`}>{STATUS_LABEL[e.status]}</span>
              <span className="spacer" />
              {deleting === e.id ? (
                <>
                  <span className="small">Delete it for good?</span>
                  <button className="btn small danger" onClick={() => remove.mutate(e.id, { onSettled: () => setDeleting(null) })}>
                    Delete
                  </button>
                  <button className="btn small" onClick={() => setDeleting(null)}>
                    Cancel
                  </button>
                </>
              ) : (
                <>
                  <Link className="btn small" to={encounterLink(c.id, e)}>
                    {e.status === 'PREPARING' ? 'Set up' : e.status === 'ENDED' ? 'View' : 'Open'}
                  </Link>
                  <button className="btn small" aria-label={`Delete ${e.name}`} onClick={() => setDeleting(e.id)}>
                    ✕
                  </button>
                </>
              )}
            </div>
          ))}
        </div>
      )}
      <form
        className="lrow add-row"
        onSubmit={(ev) => {
          ev.preventDefault()
          const n = name.trim()
          if (!n) return
          create.mutate(n, { onSuccess: (r) => navigate(`/c/${c.id}/encounters/${r.id}`) })
        }}
      >
        <input className="field w" aria-label="New encounter name" placeholder="e.g. Goblin ambush" value={name} onChange={(e) => setName(e.target.value)} />
        <button className="btn small primary" disabled={!name.trim() || create.isPending}>
          + New encounter
        </button>
      </form>
      {error && <div className="err">{(error as ApiError).message}</div>}
    </section>
  )
}

function JoinLink({ c }: { c: CampaignView }) {
  const [copied, setCopied] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const rotate = useCampaignAction(c.id, () => api('POST', `/campaigns/${c.id}/join-code/rotate`))
  const url = `${window.location.origin}/join/${c.joinCode}`
  return (
    <section className="card">
      <h3>Join link</h3>
      <p className="muted small">Send this to your players. You approve each person before they're in.</p>
      <div className="lrow">
        <code className="link-box">{url}</code>
        <button
          className="btn"
          onClick={async () => {
            await navigator.clipboard.writeText(url).catch(() => undefined)
            setCopied(true)
            setTimeout(() => setCopied(false), 2000)
          }}
        >
          {copied ? 'Copied ✓' : 'Copy'}
        </button>
        {!confirming ? (
          <button className="btn" onClick={() => setConfirming(true)}>
            New link
          </button>
        ) : (
          <>
            <span className="small">The old link stops working.</span>
            <button
              className="btn danger"
              onClick={() => {
                rotate.mutate(undefined)
                setConfirming(false)
              }}
            >
              Make a new one
            </button>
            <button className="btn" onClick={() => setConfirming(false)}>
              Cancel
            </button>
          </>
        )}
      </div>
    </section>
  )
}

function Players({ c }: { c: CampaignView }) {
  const [removing, setRemoving] = useState<string | null>(null)
  const approve = useCampaignAction(c.id, (id: string) => api('POST', `/campaigns/${c.id}/players/${id}/approve`))
  const remove = useCampaignAction(c.id, (id: string) => api('POST', `/campaigns/${c.id}/players/${id}/remove`))
  const pending = c.players.filter((p) => p.status === 'PENDING')
  const approved = c.players.filter((p) => p.status === 'APPROVED')
  const characterName = (id: string | null) => c.characters.find((ch) => ch.id === id)?.name
  const error = approve.error ?? remove.error

  return (
    <section className="card">
      <h3>Players</h3>
      {pending.length > 0 && (
        <div className="list">
          {pending.map((p) => (
            <div key={p.id} className="list-row waiting">
              <span>
                <b>{p.name}</b> <span className="muted small">wants to join</span>
              </span>
              <span className="spacer" />
              <button className="btn small primary" onClick={() => approve.mutate(p.id)}>
                Let in
              </button>
              <button className="btn small danger" onClick={() => remove.mutate(p.id)}>
                Deny
              </button>
            </div>
          ))}
        </div>
      )}
      {approved.length === 0 && pending.length === 0 && <p className="muted small">Nobody has joined yet. Send them the link above.</p>}
      <div className="list">
        {approved.map((p) => (
          <div key={p.id} className="list-row">
            <span>
              <b>{p.name}</b> <span className="muted small">{characterName(p.characterId) ?? 'hasn’t chosen a character yet'}</span>
            </span>
            <span className="spacer" />
            {removing === p.id ? (
              <>
                <span className="small">Remove {p.name}? Their character becomes unclaimed.</span>
                <button
                  className="btn small danger"
                  onClick={() => {
                    remove.mutate(p.id)
                    setRemoving(null)
                  }}
                >
                  Remove
                </button>
                <button className="btn small" onClick={() => setRemoving(null)}>
                  Cancel
                </button>
              </>
            ) : (
              <button className="btn small" onClick={() => setRemoving(p.id)}>
                Remove…
              </button>
            )}
          </div>
        ))}
      </div>
      {error && <div className="err">{(error as ApiError).message}</div>}
    </section>
  )
}

type Draft = { name: string; ac: string; hp: string; maxHp: string }
const toDraft = (ch?: CharacterDto): Draft => ({ name: ch?.name ?? '', ac: ch ? String(ch.ac) : '', hp: ch ? String(ch.hp) : '', maxHp: ch ? String(ch.maxHp) : '' })

/** Turns the form into a request, or a reason it isn't ready. */
function fromDraft(d: Draft, isNew: boolean): { body: Record<string, unknown> } | { error: string } {
  const [ac, maxHp] = [Number(d.ac), Number(d.maxHp)]
  if (!d.name.trim()) return { error: 'Give the character a name.' }
  if (d.ac === '' || d.maxHp === '' || !Number.isInteger(ac) || !Number.isInteger(maxHp) || maxHp <= 0) return { error: 'Enter AC and max HP.' }
  const hp = d.hp === '' ? (isNew ? maxHp : undefined) : Number(d.hp)
  if (hp !== undefined && (!Number.isInteger(hp) || hp < 0 || hp > maxHp)) return { error: `HP must be between 0 and ${maxHp}.` }
  return { body: { name: d.name.trim(), ac, maxHp, hp } }
}

function Characters({ c }: { c: CampaignView }) {
  const [editing, setEditing] = useState<string | null>(null)
  const [deleting, setDeleting] = useState<string | null>(null)
  const [draft, setDraft] = useState<Draft>(toDraft())
  const [formError, setFormError] = useState<string | null>(null)
  const save = useCampaignAction(c.id, ({ id, body }: { id?: string; body: Record<string, unknown> }) =>
    id ? api('PUT', `/campaigns/${c.id}/characters/${id}`, body) : api('POST', `/campaigns/${c.id}/characters`, body),
  )
  const unassign = useCampaignAction(c.id, (id: string) => api('POST', `/campaigns/${c.id}/characters/${id}/unassign`))
  const del = useCampaignAction(c.id, (id: string) => api('DELETE', `/campaigns/${c.id}/characters/${id}`))
  const error = save.error ?? unassign.error ?? del.error

  function submit(id?: string) {
    const r = fromDraft(draft, !id)
    if ('error' in r) return setFormError(r.error)
    setFormError(null)
    const portraitKey = id ? c.characters.find((ch) => ch.id === id)?.portraitKey : undefined
    save.mutate(
      { id, body: { ...r.body, portraitKey } },
      {
        onSuccess: () => {
          setEditing(null)
          setDraft(toDraft())
        },
      },
    )
  }

  const fields = (
    <>
      <input className="field grow" aria-label="Name" placeholder="Name" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
      <input className="field n" type="number" aria-label="AC" placeholder="AC" value={draft.ac} onChange={(e) => setDraft({ ...draft, ac: e.target.value })} />
      <input className="field n" type="number" aria-label="HP" placeholder="HP" value={draft.hp} onChange={(e) => setDraft({ ...draft, hp: e.target.value })} />
      <input className="field n" type="number" aria-label="Max HP" placeholder="Max HP" value={draft.maxHp} onChange={(e) => setDraft({ ...draft, maxHp: e.target.value })} />
    </>
  )

  return (
    <section className="card">
      <h3>Characters</h3>
      <p className="muted small">
        Make one for each player. They pick theirs once they're in. HP carries over from fight to fight. Click a portrait to add a picture.
      </p>
      <div className="list">
        {c.characters.map((ch) =>
          editing === ch.id ? (
            <form
              key={ch.id}
              className="list-row"
              onSubmit={(e) => {
                e.preventDefault()
                submit(ch.id)
              }}
            >
              {fields}
              <button className="btn small primary">Save</button>
              <button type="button" className="btn small" onClick={() => setEditing(null)}>
                Cancel
              </button>
            </form>
          ) : (
            <div key={ch.id} className="list-row">
              <PortraitPicker
                campaignId={c.id}
                c={ch}
                onUploaded={(portraitKey) => save.mutate({ id: ch.id, body: { name: ch.name, ac: ch.ac, maxHp: ch.maxHp, hp: ch.hp, portraitKey } })}
              />
              <b>{ch.name}</b>
              <span className="muted small num">
                AC {ch.ac} · {ch.hp}/{ch.maxHp} HP
              </span>
              <span className="small">{ch.playerName ? `Played by ${ch.playerName}` : <span className="muted">Not claimed yet</span>}</span>
              <span className="spacer" />
              {deleting === ch.id ? (
                <>
                  <span className="small">Delete {ch.name}?</span>
                  <button className="btn small danger" onClick={() => del.mutate(ch.id, { onSuccess: () => setDeleting(null) })}>
                    Delete
                  </button>
                  <button className="btn small" onClick={() => setDeleting(null)}>
                    Cancel
                  </button>
                </>
              ) : (
                <>
                  <button
                    className="btn small"
                    onClick={() => {
                      setDraft(toDraft(ch))
                      setFormError(null)
                      setEditing(ch.id)
                    }}
                  >
                    Edit
                  </button>
                  {ch.playerId && (
                    <button className="btn small" onClick={() => unassign.mutate(ch.id)} title="Lets the player choose again">
                      Unassign
                    </button>
                  )}
                  <button className="btn small danger" onClick={() => setDeleting(ch.id)}>
                    Delete…
                  </button>
                </>
              )}
            </div>
          ),
        )}
      </div>
      {editing === null && (
        <form
          className="lrow add-row"
          onSubmit={(e) => {
            e.preventDefault()
            submit()
          }}
        >
          {fields}
          <button className="btn primary">Add character</button>
        </form>
      )}
      {(formError || error) && <div className="err">{formError ?? (error as ApiError).message}</div>}
    </section>
  )
}

/** DM only: reusable monsters (portrait, AC, HP). Add enemies in the tracker starts from these. */
function Monsters({ c }: { c: CampaignView }) {
  const [editing, setEditing] = useState<string | null>(null)
  const [deleting, setDeleting] = useState<string | null>(null)
  const [draft, setDraft] = useState({ name: '', ac: '', maxHp: '' })
  const [formError, setFormError] = useState<string | null>(null)
  const save = useCampaignAction(c.id, ({ id, body }: { id?: string; body: Record<string, unknown> }) =>
    id ? api('PUT', `/campaigns/${c.id}/monster-templates/${id}`, body) : api('POST', `/campaigns/${c.id}/monster-templates`, body),
  )
  const del = useCampaignAction(c.id, (id: string) => api('DELETE', `/campaigns/${c.id}/monster-templates/${id}`))
  const error = save.error ?? del.error

  function submit(t?: TemplateDto) {
    const [ac, maxHp] = [Number(draft.ac), Number(draft.maxHp)]
    if (!draft.name.trim()) return setFormError('Give the monster a name.')
    if (draft.ac === '' || draft.maxHp === '' || !Number.isInteger(ac) || !Number.isInteger(maxHp) || maxHp <= 0)
      return setFormError('Enter AC and HP.')
    setFormError(null)
    save.mutate(
      { id: t?.id, body: { name: draft.name.trim(), ac, maxHp, portraitKey: t?.portraitKey } },
      {
        onSuccess: () => {
          setEditing(null)
          setDraft({ name: '', ac: '', maxHp: '' })
        },
      },
    )
  }

  const fields = (
    <>
      <input className="field grow" aria-label="Monster name" placeholder="Name" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
      <input className="field n" type="number" aria-label="Monster AC" placeholder="AC" value={draft.ac} onChange={(e) => setDraft({ ...draft, ac: e.target.value })} />
      <input className="field n" type="number" aria-label="Monster HP" placeholder="HP" value={draft.maxHp} onChange={(e) => setDraft({ ...draft, maxHp: e.target.value })} />
    </>
  )

  return (
    <section className="card">
      <h3>Monsters</h3>
      <p className="muted small">Save the enemies you use often. In a fight, Add enemies fills in their picture, AC and HP.</p>
      <div className="list">
        {c.templates.map((t) =>
          editing === t.id ? (
            <form
              key={t.id}
              className="list-row monster"
              onSubmit={(e) => {
                e.preventDefault()
                submit(t)
              }}
            >
              {fields}
              <button className="btn small primary">Save</button>
              <button type="button" className="btn small" onClick={() => setEditing(null)}>
                Cancel
              </button>
            </form>
          ) : (
            <div key={t.id} className="list-row monster">
              <PortraitPicker
                campaignId={c.id}
                c={t}
                onUploaded={(portraitKey) => save.mutate({ id: t.id, body: { name: t.name, ac: t.ac, maxHp: t.maxHp, portraitKey } })}
              />
              <b>{t.name}</b>
              <span className="muted small num">
                AC {t.ac} · {t.maxHp} HP
              </span>
              <span className="spacer" />
              {deleting === t.id ? (
                <>
                  <span className="small">Delete {t.name}?</span>
                  <button className="btn small danger" onClick={() => del.mutate(t.id, { onSuccess: () => setDeleting(null) })}>
                    Delete
                  </button>
                  <button className="btn small" onClick={() => setDeleting(null)}>
                    Cancel
                  </button>
                </>
              ) : (
                <>
                  <button
                    className="btn small"
                    onClick={() => {
                      setDraft({ name: t.name, ac: String(t.ac), maxHp: String(t.maxHp) })
                      setFormError(null)
                      setEditing(t.id)
                    }}
                  >
                    Edit
                  </button>
                  <button className="btn small danger" onClick={() => setDeleting(t.id)}>
                    Delete…
                  </button>
                </>
              )}
            </div>
          ),
        )}
      </div>
      {editing === null && (
        <form
          className="lrow add-row"
          onSubmit={(e) => {
            e.preventDefault()
            submit()
          }}
        >
          {fields}
          <button className="btn primary">Add monster</button>
        </form>
      )}
      {(formError || error) && <div className="err">{formError ?? (error as ApiError).message}</div>}
    </section>
  )
}

// ======================= Player =======================

function PlayerView({ c }: { c: CampaignView }) {
  const me = c.me!
  if (me.status === 'PENDING') {
    return (
      <section className="wait-card">
        <b>Waiting for the DM to let you in</b>
        You joined as {me.name}. This page updates by itself.
      </section>
    )
  }
  const mine = c.characters.find((ch) => ch.id === me.characterId)
  if (!mine) return <ChooseCharacter c={c} />
  return (
    <>
      {c.encounters.map((e) => (
        <section key={e.id} className="card fight-on">
          <div className="lrow">
            <span>
              <b>{e.name}</b>{' '}
              <span className="muted small">{e.status === 'ACTIVE' ? 'is under way' : 'is starting: initiative is being rolled'}</span>
            </span>
            <span className="spacer" />
            <Link className="btn primary" to={`/combat/${e.id}`}>
              Join the fight ▶
            </Link>
          </div>
        </section>
      ))}
      <MyCharacter c={c} ch={mine} />
      <section className="card">
        <h3>The party</h3>
        <div className="list">
          {c.characters
            .filter((ch) => ch.id !== mine.id)
            .map((ch) => (
              <div key={ch.id} className="list-row">
                <Portrait c={ch} size="mini" />
                <b>{ch.name}</b>
                <span className="muted small num">
                  AC {ch.ac} · {ch.hp}/{ch.maxHp} HP
                </span>
                <span className="small muted">{ch.playerName ?? 'Not claimed yet'}</span>
              </div>
            ))}
        </div>
      </section>
      {c.encounters.length === 0 && <p className="muted small">When the DM starts a fight, it opens from here.</p>}
    </>
  )
}

function ChooseCharacter({ c }: { c: CampaignView }) {
  const [picked, setPicked] = useState<string | null>(null)
  const claim = useCampaignAction(c.id, (id: string) => api('POST', `/campaigns/${c.id}/characters/${id}/claim`))
  const open = c.characters.filter((ch) => !ch.playerId)
  return (
    <section className="card">
      <h3>Choose your character</h3>
      <p className="muted small">You choose once. After that, only the DM can change it.</p>
      {open.length === 0 && <p className="muted">The DM hasn’t made a character for you yet. This page updates by itself.</p>}
      <div className="pick-grid">
        {open.map((ch) => (
          <button key={ch.id} className="pick" aria-pressed={picked === ch.id} onClick={() => setPicked(ch.id)}>
            <Portrait c={ch} />
            <b>{ch.name}</b>
            <span className="muted small num">
              AC {ch.ac} · {ch.maxHp} HP
            </span>
          </button>
        ))}
      </div>
      {picked && (
        <div className="lrow">
          <span className="spacer" />
          <button className="btn primary" disabled={claim.isPending} onClick={() => claim.mutate(picked)}>
            I’m {c.characters.find((ch) => ch.id === picked)?.name}
          </button>
        </div>
      )}
      {claim.error && <div className="err">{(claim.error as ApiError).message}</div>}
    </section>
  )
}

function MyCharacter({ c, ch }: { c: CampaignView; ch: CharacterDto }) {
  const [text, setText] = useState('')
  const [saved, setSaved] = useState(false)
  const setHp = useCampaignAction(c.id, (hp: number) => api('PATCH', `/campaigns/${c.id}/characters/${ch.id}/hp`, { hp }))
  const pct = Math.round((ch.hp / ch.maxHp) * 100)
  const level = pct > 50 ? 'high' : pct > 25 ? 'mid' : 'low'

  function submit() {
    const next = applyHpEdit(text, ch.hp, ch.maxHp)
    if (next === null) return
    setHp.mutate(next, {
      onSuccess: () => {
        setText('')
        setSaved(true)
        setTimeout(() => setSaved(false), 1500)
      },
    })
  }

  return (
    <section className="card mine">
      <div className="mine-row">
        <Portrait c={ch} />
        <div className="grow">
          <div className="name-lg">
            {ch.name} <span className="tag you">You</span>
          </div>
          <div className="muted small">AC {ch.ac} · set by the DM</div>
          <div className="hpbar-lg">
            <i className={level} style={{ width: `${pct}%` }} />
          </div>
        </div>
        <form
          className="lrow"
          onSubmit={(e) => {
            e.preventDefault()
            submit()
          }}
        >
          <span className="num hp-lg">
            {ch.hp}
            <span className="muted">/{ch.maxHp}</span>
          </span>
          <input className="field n" aria-label="Change your HP" placeholder="-5, +3, =20" value={text} onChange={(e) => setText(e.target.value)} />
          <button className="btn small">Set HP</button>
          {saved && <span className="ok small">✓ Saved</span>}
        </form>
      </div>
      {setHp.error && <div className="err">{(setHp.error as ApiError).message}</div>}
    </section>
  )
}
