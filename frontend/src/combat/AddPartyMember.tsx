import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import type { CampaignView } from '../api/campaign'
import { api } from '../api/client'
import type { EncounterView } from '../api/types'
import { parseInitiative } from './initiative'
import { charactersNotInFight } from './partyMembers'
import { Portrait } from './Portrait'
import type { Act } from './Tracker'

/**
 * DM only: bring a campaign character into a fight that's already going, e.g. a player who arrived late.
 * They join with their saved HP, their player runs them, and their HP is saved at End combat like everyone's.
 */
export function AddPartyMember({ view, act, onClose }: { view: EncounterView; act: Act; onClose: () => void }) {
  const campaign = useQuery({
    queryKey: ['campaign', view.campaignId],
    queryFn: () => api<CampaignView>('GET', `/campaigns/${view.campaignId}`),
    retry: false,
  })
  const [picked, setPicked] = useState<string | null>(null)
  const [initiative, setInitiative] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const available = campaign.data ? charactersNotInFight(campaign.data.characters, view) : []
  const ch = available.find((c) => c.id === picked)

  async function add() {
    if (!ch) return setError('Pick a character.')
    const init = parseInitiative(initiative)
    if (init === null) return setError(`Enter ${ch.name}’s initiative.`)
    setError(null)
    setBusy(true)
    const ok = await act({
      type: 'AddCombatant',
      id: ch.id,
      name: ch.name,
      side: 'PARTY',
      playerId: ch.playerId,
      initiative: init,
      ac: ch.ac,
      hp: ch.hp,
      maxHp: ch.maxHp,
      portraitKey: ch.portraitKey,
    })
    setBusy(false)
    if (ok) onClose()
  }

  return (
    <div className="card">
      <h3>Add a party member</h3>
      {campaign.isPending && <p className="muted small">Loading characters…</p>}
      {campaign.error && <p className="muted small">Couldn’t load the campaign’s characters.</p>}
      {campaign.data && available.length === 0 && <p className="muted small">Every character is already in this fight.</p>}
      {available.length > 0 && (
        <div className="lrow">
          {available.map((c) => (
            <button key={c.id} type="button" className="chip with-pic" aria-pressed={picked === c.id} onClick={() => setPicked(c.id)}>
              <Portrait c={c} size="mini" /> {c.name}
            </button>
          ))}
        </div>
      )}
      {ch && (
        <div className="lrow">
          <span className="muted small num">
            AC {ch.ac} · {ch.hp}/{ch.maxHp} HP · {ch.playerName ? `played by ${ch.playerName}` : 'run by you'}
          </span>
          <span className="spacer" />
          <label className="small">
            Initiative{' '}
            <input
              className="field n"
              type="number"
              inputMode="numeric"
              aria-label={`Initiative for ${ch.name}`}
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
        <button type="button" className="btn primary" disabled={busy || !ch} onClick={add}>
          Add to the fight
        </button>
      </div>
    </div>
  )
}
