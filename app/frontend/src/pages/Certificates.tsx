import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, query } from '../lib/api'
import { formatInstant } from '../lib/format'
import { useLoad } from '../lib/hooks'
import { label } from '../lib/labels'
import type { Asset, Certificate } from '../lib/types'
import { Badge, CertificateBadge, Card, Empty, Field, Loading, LoadError, Page } from '../components/ui'

export function Certificates() {
  const [status, setStatus] = useState('')
  const assets = useLoad(() => api.get<Asset[]>('/assets'), [])
  const certificates = useLoad(() => api.get<Certificate[]>('/certificates' + query({ status })), [status])
  const assetName = (id: string) => assets.data?.find((a) => a.id === id)?.name ?? id

  return (
    <Page title="Certificados" subtitle="Globales o parciales por subsistema, con su vigencia.">
      <Card>
        <div className="filters" style={{ marginBottom: 14 }}>
          <Field label="Estado">
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">Todos</option>
              {(['VALID', 'SUSPENDED', 'EXPIRED'] as const).map((s) => <option key={s} value={s}>{label(s)}</option>)}
            </select>
          </Field>
        </div>
        {certificates.error && <LoadError message={certificates.error} retry={certificates.reload} />}
        {certificates.loading && !certificates.data && <Loading />}
        {certificates.data &&
          (certificates.data.length === 0 ? (
            <Empty title="No hay certificados" hint="Se emiten desde una inspección cerrada." />
          ) : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Activo</th><th>Alcance</th><th>Emitido</th><th>Vence</th><th>Estado</th></tr></thead>
                <tbody>
                  {certificates.data.map((c) => (
                    <tr key={c.id}>
                      <td><Link to={`/certificados/${c.id}`}>{assetName(c.assetId)}</Link></td>
                      <td>{c.scope === 'PARTIAL' ? <Badge tone="seal">{label(c.subsystem)}</Badge> : 'Global'}{c.mode === 'CONDITIONAL' && <> <Badge tone="warn">Condicional</Badge></>}</td>
                      <td>{formatInstant(c.issuedAt)}</td>
                      <td>{formatInstant(c.expiresAt)}</td>
                      <td><CertificateBadge status={c.status} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
      </Card>
    </Page>
  )
}
