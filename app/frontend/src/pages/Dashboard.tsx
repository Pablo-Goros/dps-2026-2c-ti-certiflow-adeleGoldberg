import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { formatDate, formatInstant } from '../lib/format'
import { label } from '../lib/labels'
import { useLoad } from '../lib/hooks'
import type { Asset, Certificate, Finding, InspectionRow } from '../lib/types'
import { Icon, type IconName } from '../components/Icon'
import { useParties } from '../components/Party'
import { Badge, Card, CertificateBadge, Empty, InspectionBadge, Loading, LoadError, Page } from '../components/ui'

function Stat({ to, icon, tone, value, text }: { to: string; icon: IconName; tone: string; value: number; text: string }) {
  return (
    <Link className="stat" to={to}>
      <span className={`stat-icon tone-${tone}`}><Icon name={icon} size={22} /></span>
      <span>
        <strong>{value}</strong>
        <span className="stat-label">{text}</span>
      </span>
    </Link>
  )
}

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
          <ol className="welcome-steps">
            <li><span>Registrá a las <Link to="/personas">personas</Link> que van a responsabilizarse e inspeccionar.</span></li>
            <li><span>Creá un <Link to="/esquemas">esquema de inspección</Link> y publicalo.</span></li>
            <li><span>Registrá los <Link to="/activos">activos</Link> y asigná su primera <Link to="/inspecciones">inspección</Link>.</span></li>
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
            <Stat to="/activos" icon="box" tone="seal" value={assets.data!.length} text="Activos" />
            <Stat to="/inspecciones" icon="clipboard" tone="info" value={open.length} text="Inspecciones abiertas" />
            <Stat to="/hallazgos" icon="alert" tone="warn" value={findings.data!.length} text="Hallazgos con acciones abiertas" />
            <Stat to="/certificados" icon="shieldCheck" tone="ok" value={valid.length} text="Certificados vigentes" />
            <Stat to="/certificados" icon="pause" tone="bad" value={suspended.length} text="Certificados suspendidos" />
          </div>
          <div className="grid-2">
            <Card title="Inspecciones abiertas" actions={<Link className="section-link" to="/inspecciones">Ver todas <Icon name="arrowRight" size={14} /></Link>}>
              {open.length === 0 ? <Empty title="Nada pendiente" /> : (
                <ul className="mini-list">
                  {open.slice(0, 6).map((i) => (
                    <li key={i.id}>
                      <span className="grow">
                        <Link to={`/inspecciones/${i.id}`}>{assetName(i.assetId)}</Link>
                        <span className="meta"><Icon name="calendar" size={13} /> {formatDate(i.expectedDate)}</span>
                      </span>
                      <InspectionBadge status={i.status} />
                    </li>
                  ))}
                </ul>
              )}
            </Card>
            <Card title="Próximos vencimientos" actions={<Link className="section-link" to="/certificados">Ver todos <Icon name="arrowRight" size={14} /></Link>}>
              {soon.length === 0 ? <Empty title="No hay certificados vigentes" /> : (
                <ul className="mini-list">
                  {soon.map((c) => (
                    <li key={c.id}>
                      <span className="grow">
                        <Link to={`/certificados/${c.id}`}>{assetName(c.assetId)}</Link>
                        <span className="meta"><Icon name="clock" size={13} /> Vence {formatInstant(c.expiresAt)}</span>
                      </span>
                      {c.scope === 'PARTIAL' ? <Badge tone="seal" icon="layers">{label(c.subsystem)}</Badge> : <Badge icon="shield">Global</Badge>}
                      <CertificateBadge status={c.status} />
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        </>
      )}
    </Page>
  )
}
