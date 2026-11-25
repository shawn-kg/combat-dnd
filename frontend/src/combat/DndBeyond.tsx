import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import type { CharacterDto } from '../api/campaign'
import { api, ApiError } from '../api/client'
import { isDndBeyondLink } from './dndbeyond'


/** Opens the character's D&D Beyond sheet in a new tab. Nothing is fetched from D&D Beyond. */
export function DndBeyondLink({ url, children, className = '' }: { url: string | null | undefined; children?: ReactNode; className?: string }) {
  if (!isDndBeyondLink(url)) return null
  return (
    <a className={`ddb-link ${className}`} href={url} target="_blank" rel="noopener noreferrer" title="Open the D&D Beyond sheet in a new tab">
      {children ?? 'D&D Beyond'} <span aria-hidden="true">↗</span>
    </a>
  )
}

/** Paste, change or remove a character's D&D Beyond link. For the DM (any character) or a player (their own). */
export function DndBeyondField({ campaignId, ch }: { campaignId: string; ch: CharacterDto }) {
  const qc = useQueryClient()
  const [text, setText] = useState(ch.dndBeyondUrl ?? '')
  const [saved, setSaved] = useState(false)
  const save = useMutation({
    mutationFn: (url: string | null) => api<CharacterDto>('PATCH', `/campaigns/${campaignId}/characters/${ch.id}/dndbeyond`, { url }),
    onSuccess: (r) => {
      setText(r.dndBeyondUrl ?? '')
      setSaved(true)
      setTimeout(() => setSaved(false), 1500)
      qc.invalidateQueries({ queryKey: ['campaign', campaignId] })
    },
  })
  const changed = text.trim() !== (ch.dndBeyondUrl ?? '')
  const submit = () => changed && save.mutate(text.trim() || null)

  return (
    <div className="ddb-field">
      <div className="lrow">
        <input
          className="field grow"
          aria-label={`D&D Beyond link for ${ch.name}`}
          placeholder="D&D Beyond link: https://www.dndbeyond.com/characters/…"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key !== 'Enter') return
            e.preventDefault() // this can sit inside another form; don't submit that one
            submit()
          }}
        />
        <button type="button" className="btn small" disabled={!changed || save.isPending} onClick={submit}>
          {text.trim() === '' && ch.dndBeyondUrl ? 'Remove link' : 'Save link'}
        </button>
        {saved && <span className="ok small">✓ Saved</span>}
        {!changed && <DndBeyondLink url={ch.dndBeyondUrl}>Open sheet</DndBeyondLink>}
      </div>
      {save.error && <div className="err">{(save.error as ApiError).message}</div>}
    </div>
  )
}
