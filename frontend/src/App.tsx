import { Link, Route, Routes } from 'react-router-dom'
import { CombatPage } from './pages/CombatPage'
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
        <Route path="/" element={<Placeholder title="Combat D&D" next="Open your campaign's join link to get started." />} />
        <Route path="/join/:code" element={<Placeholder title="Join the campaign" next="Join and lobby: roadmap step 5." />} />
        <Route path="/c/:campaignId" element={<Placeholder title="Campaign" next="Campaign home: roadmap step 5." />} />
        <Route
          path="/c/:campaignId/encounters/:encounterId"
          element={<Placeholder title="Encounter prep" next="Encounter prep: roadmap step 5." />}
        />
        <Route path="/combat/:encounterId" element={<CombatPage />} />
      </Routes>
    </>
  )
}
