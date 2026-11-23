import { Link, Route, Routes } from 'react-router-dom'
import { CampaignPage } from './pages/CampaignPage'
import { CombatPage } from './pages/CombatPage'
import { HomePage } from './pages/HomePage'
import { JoinPage } from './pages/JoinPage'
import { Placeholder } from './pages/Placeholder'

export default function App() {
  return (
    <>
      <header className="app-bar">
        <Link to="/" className="logo">
          COMBAT D&amp;D
        </Link>
      </header>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/join/:code" element={<JoinPage />} />
        <Route path="/c/:campaignId" element={<CampaignPage />} />
        <Route
          path="/c/:campaignId/encounters/:encounterId"
          element={<Placeholder title="Encounter prep" next="Encounter prep: roadmap step 4." />}
        />
        <Route path="/combat/:encounterId" element={<CombatPage />} />
      </Routes>
    </>
  )
}
