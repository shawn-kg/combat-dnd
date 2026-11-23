import { useState } from 'react'
import { applyTheme, NEXT_THEME, savedTheme, type Theme } from './theme'

const ICON: Record<Theme, string> = { system: '◐', light: '☀', dark: '☾' }
const LABEL: Record<Theme, string> = { system: 'Theme: same as this computer', light: 'Theme: light', dark: 'Theme: dark' }

/** Cycles the color theme: same as the computer, light, dark. */
export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(savedTheme)
  return (
    <button
      type="button"
      className="theme-toggle"
      title={`${LABEL[theme]} (click to change)`}
      aria-label={`${LABEL[theme]}. Change theme`}
      onClick={() => {
        const next = NEXT_THEME[theme]
        applyTheme(next)
        setTheme(next)
      }}
    >
      {ICON[theme]}
    </button>
  )
}
