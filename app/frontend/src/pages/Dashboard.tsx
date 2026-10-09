import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { formatDate, formatInstant } from '../lib/format'
import { label } from '../lib/labels'
import { useLoad } from '../lib/hooks'
import type { Asset, Certificate, Finding, InspectionRow } from '../lib/types'
import { useParties } from '../components/Party'
import { Badge, Card, CertificateBadge, Empty, InspectionBadge, Loading, LoadError, Page } from '../components/ui'

export function Dashboard() {
  const { parties, loaded } = useParties()
  const assets = useLoad(() => api.get<Asset[]>('/assets'), [])
  const inspections = useLoad(() => api.get<InspectionRow[]>('/inspections'), [])
  const certificates = useLoad(() => api.get<Certificate[]>('/certificates'), [])
  const findings = useLoad(() => api.get<Finding[]>('/findings?openActions=true'), [])
  const failed = assets.error ?? inspections.error ?? certificates.error ?? findings.error

  const loading = !assets.data || !inspections.data || !certificates.data || !findings.data
  const assetName = (id: string) => assets.data?.find((a) => a.id === id)?.name ?? id
  const open = inspections.data?.filter((i) => i.status !== 'CLOSED') ?? []
  const valid = certificates.data?.filter((c) => c.status === 'VALID') ?? []
  const suspended = certificates.data?.filter((c) => c.status === 'SUSPENDED') ?? []
  const soon = [...valid].sort((a, b) => a.expiresAt.localeCompare(b.expiresAt)).slice(0, 5)

  if (loaded && parties.length === 0 && assets.data?.length === 0) {
    return (
      <Page title="Bienvenido a CertiFlow" subtitle="Gestioná inspecciones y certificados de laboratorios, fábricas e instalaciones.">
        <Card title="Para empezar">
          <ol>
            <li>Registrá a las <Link to="/personas">personas</Link> que van a responsabilizarse e inspeccionar.</li>
            <li>Creá un <Link to="/esquemas">esquema de inspección</Link> y publicalo.</li>
            <li>Registrá los <Link to="/activos">activos</Link> y asigná su primera <Link to="/inspecciones">inspección</Link>.</li>
          </ol>
        </Card>
      </Page>
    )
  }

  return (
    <Page title="Panel" subtitle="Lo que necesita atención ahora.">
      {failed && <LoadError message={failed} retry={() => { assets.reload(); inspections.reload(); certificates.reload(); findings.reload() }} />}
      {loading && !failed && <Loading />}
      {!loading && (
        <>
          <div className="stats">
            <Link className="stat" to="/activos"><strong>{assets.data!.length}</strong><span>Activos</span></Link>
            <Link className="stat" to="/inspecciones"><strong>{open.length}</strong><span>Inspecciones abiertas</span></Link>
            <Link className="stat" to="/hallazgos"><strong>{findings.data!.length}</strong><span>Hallazgos con acciones abiertas</span></Link>
            <Link className="stat" to="/certificados"><strong>{valid.length}</strong><span>Certificados vigentes</span></Link>
            <Link className="stat" to="/certificados"><strong>{suspended.length}</strong><span>Certificados suspendidos</span></Link>
          </div>
          <div className="grid-2">
            <Card title="Inspecciones abiertas">
              {open.length === 0 ? <Empty title="Nada pendiente" /> : (
                <div className="table-wrap"><table><tbody>
                  {open.slice(0, 6).map((i) => (
                    <tr key={i.id}>
                      <td><Link to={`/inspecciones/${i.id}`}>{assetName(i.assetId)}</Link></td>
                      <td>{formatDate(i.expectedDate)}</td>
                      <td><InspectionBadge status={i.status} /></td>
                    </tr>
                  ))}
                </tbody></table></div>
              )}
            </Card>
            <Card title="Próximos vencimientos">
              {soon.length === 0 ? <Empty title="No hay certificados vigentes" /> : (
                <div className="table-wrap"><table><tbody>
                  {soon.map((c) => (
                    <tr key={c.id}>
                      <td><Link to={`/certificados/${c.id}`}>{assetName(c.assetId)}</Link></td>
                      <td>{c.scope === 'PARTIAL' ? <Badge tone="seal">{label(c.subsystem)}</Badge> : 'Global'}</td>
                      <td>{formatInstant(c.expiresAt)}</td>
                      <td><CertificateBadge status={c.status} /></td>
                    </tr>
                  ))}
                </tbody></table></div>
              )}
            </Card>
          </div>
        </>
      )}
    </Page>
  )
}
