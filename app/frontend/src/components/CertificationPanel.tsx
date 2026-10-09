import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError, query } from '../lib/api'
import { useLoad } from '../lib/hooks'
import { label, humanize } from '../lib/labels'
import type { Assessment, Certificate } from '../lib/types'
import { Blockers } from './Blockers'
import { Badge, Card, CertificateBadge, ErrorLine, Loading, Notice, useAction, useToast } from './ui'

interface Target {
  /** null = the global certificate. */
  subsystem: string | null
}

/** Whether the inspection can be certified, subsystem by subsystem and as a whole, and the buttons to do it. */
export function CertificationPanel({ inspectionId, assetId, subsystems }: {
  inspectionId: string
  assetId: string
  subsystems: string[]
}) {
  const issued = useLoad(() => api.get<Certificate[]>('/certificates' + query({ inspectionId })), [inspectionId])
  const assetCertificates = useLoad(() => api.get<Certificate[]>('/certificates' + query({ assetId })), [assetId])
  const derivation = useLoad(
    () => (subsystems.length ? api.get<{ type: string; reasons?: string[] }>(`/inspections/${inspectionId}/global-derivation`) : Promise.resolve(null)),
    [inspectionId, subsystems.length],
  )
  const targets: Target[] = [...subsystems.map((subsystem) => ({ subsystem })), { subsystem: null }]
  const reloadAll = () => {
    issued.reload()
    assetCertificates.reload()
    derivation.reload()
  }

  return (
    <Card title="Certificación">
      {subsystems.length > 0 && (
        <p className="muted">
          Cada subsistema se certifica por separado. El certificado global se puede emitir cuando todos los parciales están en regla.
        </p>
      )}
      {issued.loading && !issued.data ? <Loading /> : (
        <div className="stack">
          {targets.map((target) => (
            <TargetRow
              key={target.subsystem ?? 'global'}
              inspectionId={inspectionId}
              target={target}
              certificate={issued.data?.find((c) => (target.subsystem ? c.subsystem === target.subsystem : c.scope === 'GLOBAL'))}
              previous={assetCertificates.data?.find(
                (c) => c.backingInspectionId !== inspectionId && (target.subsystem ? c.subsystem === target.subsystem : c.scope === 'GLOBAL'),
              )}
              onChange={reloadAll}
            />
          ))}
        </div>
      )}
      {derivation.data && (
        <div style={{ marginTop: 14 }}>
          {derivation.data.type === 'Derived' ? (
            <Notice kind="ok">Los certificados parciales vigentes componen un certificado global.</Notice>
          ) : (
            <Notice kind="info">
              Todavía no se puede componer un global a partir de los parciales{derivation.data.reasons?.length ? ':' : '.'}
              {derivation.data.reasons && <ul>{derivation.data.reasons.map((r) => <li key={r}>{r}</li>)}</ul>}
            </Notice>
          )}
        </div>
      )}
    </Card>
  )
}

function TargetRow({ inspectionId, target, certificate, previous, onChange }: {
  inspectionId: string
  target: Target
  certificate: Certificate | undefined
  previous: Certificate | undefined
  onChange: () => void
}) {
  const toast = useToast()
  const subsystemPath = target.subsystem ? `?subsystem=${encodeURIComponent(target.subsystem)}` : ''
  const eligibility = useLoad(
    () => api.get<Assessment>(`/inspections/${inspectionId}/eligibility${subsystemPath}`),
    [inspectionId, subsystemPath, certificate?.id],
  )
  const { run, busy, error } = useAction()
  const [blocked, setBlocked] = useState<Assessment | null>(null)
  const title = target.subsystem ? label(target.subsystem) : 'Certificado global'

  async function issue(path: 'certificates' | 'certificates/renewal') {
    setBlocked(null)
    try {
      const reply = await api.post<{ outcome: string }>(`/inspections/${inspectionId}/${path}`, target.subsystem ? { subsystem: target.subsystem } : {})
      toast.show(reply.outcome === 'ALREADY_ISSUED' ? 'Este certificado ya estaba emitido' : path === 'certificates' ? 'Certificado emitido' : 'Certificado renovado')
      onChange()
    } catch (failure) {
      const body = failure instanceof ApiError ? (failure.body as { outcome?: string; assessment?: Assessment } | null) : null
      if (body?.outcome === 'BLOCKED' && body.assessment) {
        setBlocked(body.assessment)
        eligibility.reload()
      } else {
        throw failure
      }
    }
  }

  const blockers = (blocked ?? eligibility.data)?.blockers ?? []
  const eligible = !!eligibility.data && eligibility.data.blockers.length === 0

  return (
    <div className="criterion">
      <header>
        <div>
          <strong>{title}</strong>{' '}
          {certificate ? (
            <>
              <CertificateBadge status={certificate.status} />{' '}
              <Link to={`/certificados/${certificate.id}`}>Ver certificado</Link>
            </>
          ) : eligibility.data ? (
            eligible ? <Badge tone="ok">Se puede emitir{eligibility.data.mode ? ` (${label(String(eligibility.data.mode)).toLowerCase()})` : ''}</Badge> : <Badge tone="warn">Bloqueado</Badge>
          ) : null}
        </div>
        {!certificate && (
          <div className="row">
            {previous && (
              <button disabled={busy || !eligible} title={`Renueva el certificado anterior (${humanize(previous.status)})`} onClick={() => run(() => issue('certificates/renewal'))}>
                Renovar
              </button>
            )}
            <button className="primary" disabled={busy || !eligible} onClick={() => run(() => issue('certificates'))}>
              Emitir certificado
            </button>
          </div>
        )}
      </header>
      {eligibility.error && <ErrorLine message={eligibility.error} />}
      {!certificate && blockers.length > 0 && (
        <Notice kind="warn">
          No se puede emitir todavía:
          <Blockers blockers={blockers} />
        </Notice>
      )}
      <ErrorLine message={error} />
    </div>
  )
}
