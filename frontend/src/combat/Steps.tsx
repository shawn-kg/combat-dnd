const STEPS = [
  ['prep', 'Prep'],
  ['initiative', 'Initiative'],
  ['combat', 'Combat'],
] as const

/** Where the DM is in setting up a fight: Prep, then Initiative, then Combat. */
export function Steps({ on }: { on: (typeof STEPS)[number][0] }) {
  return (
    <span className="steps" aria-label="Phase">
      {STEPS.map(([key, label]) => (
        <span key={key} className={key === on ? 'on' : ''} aria-current={key === on ? 'step' : undefined}>
          {label}
        </span>
      ))}
    </span>
  )
}
