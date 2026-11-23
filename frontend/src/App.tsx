import { Link, Route, Routes, useLocation } from 'react-router-dom'
import { ErrorBoundary } from './ErrorBoundary'
import { CampaignPage } from './pages/CampaignPage'
import { CombatPage } from './pages/CombatPage'
import { DmSignInPage } from './pages/DmSignInPage'
import { HomePage } from './pages/HomePage'
import { JoinPage } from './pages/JoinPage'
import { PrepPage } from './pages/PrepPage'
import { ThemeToggle } from './ThemeToggle'

export default function App() {
  const location = useLocation()
  return (
    <>
      <header className="app-bar">
        <Link to="/" className="logo">
          COMBAT D&amp;D
        </Link>
        <span className="spacer" />
        <ThemeToggle />
      </header>
      {/* Keyed by page, so moving to another page clears a crash. */}
      <ErrorBoundary key={location.pathname}>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/join/:code" element={<JoinPage />} />
          <Route path="/dm/:code" element={<DmSignInPage />} />
          <Route path="/c/:campaignId" element={<CampaignPage />} />
          <Route path="/c/:campaignId/encounters/:encounterId" element={<PrepPage />} />
          <Route path="/combat/:encounterId" element={<CombatPage />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </ErrorBoundary>
    </>
  )
}

function NotFound() {
  return (
    <main className="page stack">
      <h1 className="title">Page not found</h1>
      <p className="muted">That link doesn’t go anywhere. If someone sent it to you, ask them for it again.</p>
      <Link to="/">Go to the start</Link>
    </main>
  )
}
