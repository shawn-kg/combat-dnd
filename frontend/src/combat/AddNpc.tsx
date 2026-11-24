import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import type { CampaignView } from '../api/campaign'
import { api } from '../api/client'
import type { EncounterView } from '../api/types'
import { parseInitiative } from './initiative'
import { Portrait } from './Portrait'
import type { Act } from './Tracker'

/** DM only: bring one of the campaign's NPCs into a fight that's already going, with their saved HP. */
export function AddNpc({ view, act, onClose }: { view: EncounterView; act: Act; onClose: () => void }) {
  const campaign = useQuery({
    queryKey: ['campaign', view.campaignId],
    queryFn: () => api<CampaignView>('GET', `/campaigns/${view.campaignId}`),
    retry: false,
  })
  const [picked, setPicked] = useState<string | null>(null)
  const [initiative, setInitiative] = useState('')
  const [hidden, setHidden] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const inFight = new Set(view.combatants.map((c) => c.id))
  const available = (campaign.data?.npcs ?? []).filter((n) => !inFight.has(n.id))
  const npc = available.find((n) => n.id === picked)

  async function add() {
    if (!npc) return setError('Pick an NPC.')
    const init = parseInitiative(initiative)
    if (init === null) return setError(`Enter ${npc.name}’s initiative.`)
    setError(null)
    setBusy(true)
    const ok = await act({
      type: 'AddCombatant',
      id: npc.id,
      name: npc.name,
      side: 'NPC',
      initiative: init,
      ac: npc.ac,
      hp: npc.hp,
      maxHp: npc.maxHp,
      portraitKey: npc.portraitKey,
      hidden,
      statsVisible: npc.statsVisible,
    })
    setBusy(false)
    if (ok) onClose()
  }

  return (
    <div className="card">
      <h3>Add an NPC</h3>
      {campaign.isPending && <p className="muted small">Loading NPCs…</p>}
      {campaign.error && <p className="muted small">Couldn’t load the campaign’s NPCs.</p>}
      {campaign.data && campaign.data.npcs.length === 0 && <p className="muted small">No NPCs yet. Add them on the campaign page.</p>}
      {campaign.data && campaign.data.npcs.length > 0 && available.length === 0 && (
        <p className="muted small">Every NPC is already in this fight.</p>
      )}
      {available.length > 0 && (
        <div className="lrow">
          {available.map((n) => (
            <button key={n.id} type="button" className="chip with-pic" aria-pressed={picked === n.id} onClick={() => setPicked(n.id)}>
              <Portrait c={n} size="mini" /> {n.name}
            </button>
          ))}
        </div>
      )}
      {npc && (
        <div className="lrow">
          <span className="muted small num">
            AC {npc.ac} · {npc.hp}/{npc.maxHp} HP · {npc.statsVisible ? 'players see AC and HP' : 'AC and HP hidden from players'}
          </span>
          <span className="spacer" />
          <label className="small">
            <input type="checkbox" checked={hidden} onChange={(e) => setHidden(e.target.checked)} /> Hidden
          </label>
          <label className="small">
            Initiative{' '}
            <input
              className="field n"
              type="number"
              inputMode="numeric"
              aria-label={`Initiative for ${npc.name}`}
              value={initiative}
              onChange={(e) => setInitiative(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && add()}
              autoFocus
            />
          </label>
        </div>
      )}
      {error && <div className="err">{error}</div>}
      <div className="lrow">
        <span className="spacer" />
        <button type="button" className="btn" onClick={onClose}>
          Cancel
        </button>
        <button type="button" className="btn primary" disabled={busy || !npc} onClick={add}>
          Add to the fight
        </button>
      </div>
    </div>
  )
}
