import { useState, type ReactNode } from 'react'

/**
 * DM inline editing: shows the value as a button; clicking turns it into a text box.
 * Enter or leaving the box saves, Escape cancels. `onCommit` gets the raw text and decides what it means.
 */
export function Editable({
  enabled,
  display,
  initial,
  label,
  placeholder,
  className,
  onCommit,
}: {
  enabled: boolean
  display: ReactNode
  /** What the box starts with: the current name, or empty for numbers so you can type `-5` straight away. */
  initial: string
  label: string
  placeholder?: string
  className?: string
  onCommit: (text: string) => void
}) {
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState(initial)

  if (!enabled) return <span className={className}>{display}</span>
  if (!editing) {
    return (
      <button
        type="button"
        className={`editable ${className ?? ''}`}
        title="Click to edit"
        aria-label={`Edit ${label}`}
        onClick={() => {
          setText(initial)
          setEditing(true)
        }}
      >
        {display}
      </button>
    )
  }
  const finish = (save: boolean) => {
    setEditing(false)
    if (save && text.trim() !== '' && text.trim() !== initial) onCommit(text.trim())
  }
  return (
    <input
      className="edit-in num"
      autoFocus
      aria-label={label}
      value={text}
      placeholder={placeholder}
      onChange={(e) => setText(e.target.value)}
      onFocus={(e) => e.target.select()}
      onBlur={() => finish(true)}
      onKeyDown={(e) => {
        if (e.key === 'Enter') finish(true)
        if (e.key === 'Escape') finish(false)
      }}
    />
  )
}
