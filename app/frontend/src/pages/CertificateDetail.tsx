import { Link, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { formatDate, formatInstant } from '../lib/format'
import { useLoad } from '../lib/hooks'
import { label } from '../lib/labels'
import type { Asset, Certificate } from '../lib/types'
import { Tree } from '../components/Tree'
import { Badge, CertificateBadge, Card, Loading, LoadError, Notice, Page } from '../components/ui'

interface Report {
  backingInspectionWasRectified: boolean
  unresolvedSuspensionCauses: string[]
  pendingCommitments: { criterionId: string; work: string; dueDate: string; status: string }[]
  policy: unknown
  previousCertificateId: string | null
}

export function CertificateDetail() {
  const { id = '' } = useParams()
  const certificate = useLoad(() => api.get<Certificate>(`/certificates/${id}`), [id])
  const report = useLoad(() => api.get<Report>(`/certificates/${id}/report`), [id])
  const asset = useLoad(
    () => (certificate.data ? api.get<Asset>(`/assets/${certificate.data.assetId}`) : Promise.resolve(undefined)),
    [certificate.data?.assetId],
  )

  if (certificate.error) return <Page title="Certificado"><LoadError message={certificate.error} retry={certificate.reload} /></Page>
  if (!certificate.data) return <Page title="Certificado"><Loading /></Page>
  const c = certificate.data
  const r = report.data

  return (
    <Page
      title="Certificado"
      actions={
        <>
          <Link className="button" to={`/inspecciones/${c.backingInspectionId}`}>Ver inspección</Link>
          <Link className="button" to={`/auditoria?type=CERTIFICATE&id=${c.id}`}>Ver auditoría</Link>
        </>
      }
    >
      <article className={`certificate ${c.status.toLowerCase()}`} aria-label="Certificado">
        <svg className="seal" viewBox="0 0 64 64" aria-hidden="true">
          <circle cx="32" cy="32" r="30" fill="none" stroke="#0f6b5c" strokeWidth="2" />
          <circle cx="32" cy="32" r="24" fill="#0f6b5c" />
          <path d="M20 33l8 8 16-17" stroke="#fff" strokeWidth="5" fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <h2>{asset.data?.name ?? c.assetId}</h2>
        <div className="row">
          <CertificateBadge status={c.status} />
          <Badge tone="seal">{c.scope === 'PARTIAL' ? `Parcial · ${label(c.subsystem)}` : 'Global'}</Badge>
          {c.mode === 'CONDITIONAL' && <Badge tone="warn">Condicional</Badge>}
        </div>
        <div className="meta">
          <div><span>Emitido</span><strong>{formatInstant(c.issuedAt)}</strong></div>
          <div><span>Vence</span><strong>{formatInstant(c.expiresAt)}</strong></div>
          <div><span>Jurisdicción</span><strong>{asset.data?.jurisdiction ?? '—'}</strong></div>
          <div><span>Responsable</span><strong>{asset.data?.responsibleName ?? '—'}</strong></div>
        </div>
      </article>

      {report.error && <LoadError message={report.error} retry={report.reload} />}
      {r && (
        <>
          {r.unresolvedSuspensionCauses.length > 0 && (
            <Notice kind="warn">
              El certificado está suspendido por causas sin resolver:
              <ul>{r.unresolvedSuspensionCauses.map((cause) => <li key={cause}>{cause}</li>)}</ul>
            </Notice>
          )}
          {r.backingInspectionWasRectified && <Notice kind="info">La inspección que lo respalda fue rectificada después de la emisión.</Notice>}
          <Card title="Compromisos pendientes">
            {r.pendingCommitments.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>No hay correcciones pendientes asociadas a este certificado.</p>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Criterio</th><th>Trabajo</th><th>Para el</th><th>Estado</th></tr></thead>
                  <tbody>
                    {r.pendingCommitments.map((p) => (
                      <tr key={p.criterionId}>
                        <td>{p.criterionId}</td><td>{p.work}</td><td>{formatDate(p.dueDate)}</td><td>{label(p.status)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
          <Card title="Política aplicada">
            <Tree value={r.policy} />
          </Card>
        </>
      )}
    </Page>
  )
}
