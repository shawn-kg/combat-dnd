/** Stand-in for screens on the roadmap (join and lobby, campaign home, encounter prep). */
export function Placeholder({ title, next }: { title: string; next: string }) {
  return (
    <main className="page">
      <h1 style={{ fontFamily: 'var(--font-display)' }}>{title}</h1>
      <p className="muted">{next}</p>
    </main>
  )
}
