import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { formatInstant } from '../lib/format'
import { useLoad } from '../lib/hooks'
import { label } from '../lib/labels'
import type { GlobalDerivation } from '../lib/types'
import { Card, ErrorLine, Loading, Notice } from './ui'

export function AssetGlobalCertification({ assetId }: { assetId: string }) {
  const result = useLoad(() => api.get<GlobalDerivation>(`/assets/${assetId}/global-derivation`), [assetId])
  return <Card title="Certificación global del activo">
    <ErrorLine message={result.error} />
    {result.loading && !result.data && <Loading />}
    {result.data && <GlobalDerivationView result={result.data} />}
  </Card>
}

export function GlobalDerivationView({ result }: { result: GlobalDerivation }) {
  if (result.type === 'NotDerivable') return <Notice kind="info">
    No se puede componer un global con los parciales actuales:
    <ul>{result.reasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>
  </Notice>
  const global = result.certificate
  return <div className="stack">
    <Notice kind="ok">El activo tiene certificación global {label(global.mode).toLowerCase()} desde {formatInstant(global.validity.issuedAt)} hasta {formatInstant(global.validity.expiresAt)}.</Notice>
    <p className="muted">Los últimos parciales de cada subsistema pueden provenir de distintas inspecciones.</p>
    <ul className="tree">{global.provenance.map((part) => <li key={part.certificateId}>
      <strong>{label(part.subsystem)}</strong>: <Link to={`/certificados/${part.certificateId}`}>certificado</Link>,{' '}
      <Link to={`/inspecciones/${part.backingInspectionId}`}>inspección</Link>,{' '}
      esquema v{part.schemaVersionId.number}, {part.policy.reference.policyId} revisión {part.policy.reference.revision} ({label(part.mode)}).
    </li>)}</ul>
  </div>
}
