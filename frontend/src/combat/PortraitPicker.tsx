import { useRef, useState } from 'react'
import { ApiError, upload } from '../api/client'
import { Portrait } from './Portrait'

/**
 * DM only: the portrait as a button. Choosing a file uploads it (the server crops it to a 256 × 256 square)
 * and hands the new key to `onUploaded`.
 */
export function PortraitPicker({
  campaignId,
  c,
  size = 'mini',
  onUploaded,
}: {
  campaignId: string
  c: { name: string; portraitKey: string | null }
  size?: 'row' | 'mini'
  onUploaded: (portraitKey: string) => void
}) {
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function onFile(file: File | undefined) {
    if (!file) return
    setBusy(true)
    setError(null)
    try {
      const r = await upload<{ portraitKey: string }>(`/campaigns/${campaignId}/portraits`, file)
      onUploaded(r.portraitKey)
    } catch (e) {
      setError((e as ApiError).message)
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
    }
  }

  return (
    <span className="portrait-picker">
      <button
        type="button"
        className={`portrait-btn${busy ? ' busy' : ''}`}
        title={c.portraitKey ? 'Change picture' : 'Add a picture'}
        aria-label={`${c.portraitKey ? 'Change' : 'Add'} picture for ${c.name}`}
        onClick={() => input.current?.click()}
        disabled={busy}
      >
        <Portrait c={c} size={size} />
        <span className="portrait-edit" aria-hidden="true">
          {busy ? '…' : '✎'}
        </span>
      </button>
      <input ref={input} type="file" accept="image/jpeg,image/png" hidden onChange={(e) => onFile(e.target.files?.[0])} />
      {error && <span className="err">{error}</span>}
    </span>
  )
}
