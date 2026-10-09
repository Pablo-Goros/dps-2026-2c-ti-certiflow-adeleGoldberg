import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { useParties } from './components/Party'
import { Dashboard } from './pages/Dashboard'
import { Parties } from './pages/Parties'
import { Assets } from './pages/Assets'
import { AssetDetail } from './pages/AssetDetail'
import { Schemas } from './pages/Schemas'
import { SchemaDetail } from './pages/SchemaDetail'
import { Inspections } from './pages/Inspections'
import { InspectionDetail } from './pages/InspectionDetail'
import { Findings } from './pages/Findings'
import { FindingDetail } from './pages/FindingDetail'
import { Certificates } from './pages/Certificates'
import { CertificateDetail } from './pages/CertificateDetail'
import { Audit } from './pages/Audit'
import { Processes } from './pages/Processes'

const links: [string, string][] = [
  ['/', 'Panel'],
  ['/personas', 'Personas'],
  ['/activos', 'Activos'],
  ['/esquemas', 'Esquemas'],
  ['/inspecciones', 'Inspecciones'],
  ['/hallazgos', 'Hallazgos'],
  ['/certificados', 'Certificados'],
  ['/auditoria', 'Auditoría'],
  ['/procesos', 'Procesos'],
]

function Seal() {
  return (
    <svg width="28" height="28" viewBox="0 0 32 32" aria-hidden="true">
      <circle cx="16" cy="16" r="14" fill="#0f6b5c" />
      <circle cx="16" cy="16" r="11" fill="none" stroke="#fff" strokeOpacity=".45" strokeWidth="1" />
      <path d="M9.5 16.5l4.5 4.5L22.5 11.5" stroke="#fff" strokeWidth="3" fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function App() {
  const { parties, actorId, chooseActor } = useParties()
  return (
    <div className="shell">
      <aside className="side">
        <div className="brand">
          <Seal /> CertiFlow
        </div>
        <nav className="nav" aria-label="Principal">
          {links.map(([to, text]) => (
            <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => (isActive ? 'active' : '')}>
              {text}
            </NavLink>
          ))}
        </nav>
        <div className="actor">
          <label htmlFor="actor">Actuando como</label>
          <select id="actor" value={actorId ?? ''} onChange={(e) => chooseActor(e.target.value || null)}>
            <option value="">Sin identificar</option>
            {parties.map((party) => (
              <option key={party.id} value={party.id}>
                {party.name}
              </option>
            ))}
          </select>
        </div>
      </aside>
      <main className="main">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/personas" element={<Parties />} />
          <Route path="/activos" element={<Assets />} />
          <Route path="/activos/:id" element={<AssetDetail />} />
          <Route path="/esquemas" element={<Schemas />} />
          <Route path="/esquemas/:id" element={<SchemaDetail />} />
          <Route path="/inspecciones" element={<Inspections />} />
          <Route path="/inspecciones/:id" element={<InspectionDetail />} />
          <Route path="/hallazgos" element={<Findings />} />
          <Route path="/hallazgos/:id" element={<FindingDetail />} />
          <Route path="/certificados" element={<Certificates />} />
          <Route path="/certificados/:id" element={<CertificateDetail />} />
          <Route path="/auditoria" element={<Audit />} />
          <Route path="/procesos" element={<Processes />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}
