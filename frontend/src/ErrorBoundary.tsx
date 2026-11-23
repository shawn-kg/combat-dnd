import { Component, type ReactNode } from 'react'

/** If a page crashes, show a way out instead of a blank screen. */
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error: unknown) {
    console.error(error)
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <main className="page stack">
        <h1 className="title">Something went wrong</h1>
        <p className="muted">That’s a bug on our side, not something you did. Reloading usually fixes it; nothing in the fight is lost.</p>
        <div className="lrow">
          <button className="btn primary" onClick={() => window.location.reload()}>
            Reload
          </button>
          <a className="btn" href="/">
            Go to the start
          </a>
        </div>
      </main>
    )
  }
}
