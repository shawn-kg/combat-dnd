import type { CombatantView } from '../api/types'
import { initials } from './lastTurn'

/** A round portrait, or the name's initials on the side's color when there is no picture yet. */
export function Portrait({ c, size = 'row' }: { c: Pick<CombatantView, 'name' | 'portraitKey'>; size?: 'row' | 'mini' }) {
  const cls = size === 'mini' ? 'mini' : 'portrait'
  return c.portraitKey ? (
    <img className={cls} src={`/api/portraits/${encodeURIComponent(c.portraitKey)}`} alt="" />
  ) : (
    <span className={`${cls} initials`} aria-hidden="true">
      {initials(c.name)}
    </span>
  )
}
