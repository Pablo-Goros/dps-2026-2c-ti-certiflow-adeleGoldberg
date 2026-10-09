import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, query } from '../lib/api'
import { formatDate, formatInstant } from '../lib/format'
import { useLoad } from '../lib/hooks'
import { label } from '../lib/labels'
import type { Asset, Certificate, InspectionRow } from '../lib/types'
import { PartySelect, useParties } from '../components/Party'
import {
  CertificateBadge, Card, Empty, ErrorLine, Facts, Field, InspectionBadge, Loading, LoadError, Page, useAction,
} from '../components/ui'

export function AssetDetail() {
  const { id = '' } = useParams()
  const { nameOf } = useParties()
  const asset = useLoad(() => api.get<Asset>(`/assets/${id}`), [id])
  const inspections = useLoad(() => api.get<InspectionRow[]>('/inspections' + query({ assetId: id })), [id])
  const certificates = useLoad(() => api.get<Certificate[]>('/certificates' + query({ assetId: id })), [id])

  if (asset.error) return <Page title="Activo"><LoadError message={asset.error} retry={asset.reload} /></Page>
  if (!asset.data) return <Page title="Activo"><Loading /></Page>
  const a = asset.data

  return (
    <Page
      title={a.name}
      subtitle={`${label(a.assetType)} · ${a.location}`}
      actions={<Link className="button primary" to={`/inspecciones?assetId=${a.id}`}>Asignar inspección</Link>}
    >
      <Card title="Datos">
        <Facts
          items={[
            ['Responsable', a.responsibleName],
            ['Jurisdicción', a.jurisdiction],
            ['Subsistemas', a.subsystems.length ? a.subsystems.map(label).join(', ') : 'Se certifica como un todo'],
            ...Object.entries(a.characteristics).map(([k, v]): [string, string] => [label(k), v]),
          ]}
        />
      </Card>

      <div className="grid-2">
        <Relocate asset={a} onDone={asset.reload} />
        <ChangeResponsible asset={a} onDone={asset.reload} />
      </div>

      <Card title="Inspecciones">
        {inspections.data && inspections.data.length === 0 ? (
          <Empty title="Este activo no tiene inspecciones" hint="Asigná una para empezar a certificarlo." />
        ) : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Fecha prevista</th><th>Inspector</th><th>Estado</th></tr></thead>
              <tbody>
                {inspections.data?.map((row) => (
                  <tr key={row.id}>
                    <td><Link to={`/inspecciones/${row.id}`}>{formatDate(row.expectedDate)}</Link></td>
                    <td>{nameOf(row.inspectorId)}</td>
                    <td><InspectionBadge status={row.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card title="Certificados">
        {certificates.data && certificates.data.length === 0 ? (
          <Empty title="Todavía no tiene certificados" />
        ) : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Alcance</th><th>Emitido</th><th>Vence</th><th>Estado</th></tr></thead>
              <tbody>
                {certificates.data?.map((c) => (
                  <tr key={c.id}>
                    <td><Link to={`/certificados/${c.id}`}>{c.scope === 'PARTIAL' ? label(c.subsystem) : 'Global'}</Link></td>
                    <td>{formatInstant(c.issuedAt)}</td>
                    <td>{formatInstant(c.expiresAt)}</td>
                    <td><CertificateBadge status={c.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </Page>
  )
}

function Relocate({ asset, onDone }: { asset: Asset; onDone: () => void }) {
  const [location, setLocation] = useState(asset.location)
  const { run, busy, error } = useAction()
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (await run(() => api.put(`/assets/${asset.id}/location`, { location: location.trim() }), 'Ubicación actualizada')) onDone()
  }
  return (
    <Card title="Cambiar ubicación">
      <form className="stack" onSubmit={submit}>
        <Field label="Nueva ubicación">
          <input value={location} onChange={(e) => setLocation(e.target.value)} required />
        </Field>
        <div><button disabled={busy || !location.trim() || location.trim() === asset.location}>Guardar ubicación</button></div>
        <ErrorLine message={error} />
      </form>
    </Card>
  )
}

function ChangeResponsible({ asset, onDone }: { asset: Asset; onDone: () => void }) {
  const [responsibleId, setResponsibleId] = useState(asset.responsibleId)
  const { run, busy, error } = useAction()
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (await run(() => api.put(`/assets/${asset.id}/responsible`, { responsibleId }), 'Responsable actualizado')) onDone()
  }
  return (
    <Card title="Cambiar responsable">
      <form className="stack" onSubmit={submit}>
        <Field label="Nuevo responsable">
          <PartySelect value={responsibleId} onChange={setResponsibleId} />
        </Field>
        <div><button disabled={busy || !responsibleId || responsibleId === asset.responsibleId}>Guardar responsable</button></div>
        <ErrorLine message={error} />
      </form>
    </Card>
  )
}
