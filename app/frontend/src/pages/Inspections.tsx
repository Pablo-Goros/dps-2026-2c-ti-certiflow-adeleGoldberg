import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, query } from '../lib/api'
import { formatDate, today } from '../lib/format'
import { useLoad } from '../lib/hooks'
import { label } from '../lib/labels'
import type { Asset, Inspection, InspectionRow, InspectionStatus } from '../lib/types'
import { PartySelect, useParties } from '../components/Party'
import { Card, Empty, ErrorLine, Field, InspectionBadge, Loading, LoadError, Page, useAction } from '../components/ui'

export function Inspections() {
  const [params] = useSearchParams()
  const presetAsset = params.get('assetId') ?? ''
  const [status, setStatus] = useState('')
  const [creating, setCreating] = useState(presetAsset !== '')
  const { nameOf } = useParties()
  const assets = useLoad(() => api.get<Asset[]>('/assets'), [])
  const rows = useLoad(
    () => api.get<InspectionRow[]>('/inspections' + query({ status: status as InspectionStatus, assetId: presetAsset })),
    [status, presetAsset],
  )
  const assetName = (id: string) => assets.data?.find((a) => a.id === id)?.name ?? id

  return (
    <Page
      title="Inspecciones"
      subtitle={presetAsset ? `Del activo ${assetName(presetAsset)}` : 'Se asignan a un inspector, se completan y se cierran.'}
      actions={
        <button className="primary" onClick={() => setCreating((open) => !open)}>
          {creating ? 'Cerrar' : 'Asignar inspección'}
        </button>
      }
    >
      {creating && assets.data && <NewInspection assets={assets.data} preset={presetAsset} />}
      <Card>
        <div className="filters" style={{ marginBottom: 14 }}>
          <Field label="Estado">
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">Todos</option>
              {(['ASSIGNED', 'IN_PROGRESS', 'CLOSED'] as const).map((s) => (
                <option key={s} value={s}>{label(s)}</option>
              ))}
            </select>
          </Field>
          {presetAsset && <Link className="button" to="/inspecciones">Ver todas</Link>}
        </div>
        {rows.error && <LoadError message={rows.error} retry={rows.reload} />}
        {rows.loading && !rows.data && <Loading />}
        {rows.data &&
          (rows.data.length === 0 ? (
            <Empty title="No hay inspecciones" hint="Asigná una a un inspector para empezar." />
          ) : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Activo</th><th>Inspector</th><th>Fecha prevista</th><th>Estado</th></tr></thead>
                <tbody>
                  {rows.data.map((row) => (
                    <tr key={row.id}>
                      <td><Link to={`/inspecciones/${row.id}`}>{assetName(row.assetId)}</Link></td>
                      <td>{nameOf(row.inspectorId)}</td>
                      <td>{formatDate(row.expectedDate)}</td>
                      <td><InspectionBadge status={row.status} /></td>
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

function NewInspection({ assets, preset }: { assets: Asset[]; preset: string }) {
  const navigate = useNavigate()
  const [assetId, setAssetId] = useState(preset)
  const [inspectorId, setInspectorId] = useState('')
  const [expectedDate, setExpectedDate] = useState(today(7))
  const { run, busy, error } = useAction()

  async function submit(event: FormEvent) {
    event.preventDefault()
    let created: Inspection | undefined
    const ok = await run(async () => {
      created = await api.post<Inspection>('/inspections', { assetId, inspectorId, expectedDate })
    }, 'Inspección asignada')
    if (ok && created) navigate(`/inspecciones/${created.id}`)
  }

  return (
    <Card title="Asignar inspección">
      <form className="form" onSubmit={submit}>
        <Field label="Activo">
          <select value={assetId} onChange={(e) => setAssetId(e.target.value)} required>
            <option value="">Elegí…</option>
            {assets.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
        </Field>
        <Field label="Inspector">
          <PartySelect value={inspectorId} onChange={setInspectorId} />
        </Field>
        <Field label="Fecha prevista">
          <input type="date" value={expectedDate} onChange={(e) => setExpectedDate(e.target.value)} required />
        </Field>
        <div className="form-actions">
          <button className="primary" disabled={busy || !assetId || !inspectorId}>Asignar</button>
        </div>
      </form>
      <ErrorLine message={error} />
    </Card>
  )
}
