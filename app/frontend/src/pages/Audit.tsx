import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, query } from '../lib/api'
import { formatInstant } from '../lib/format'
import { useLoad } from '../lib/hooks'
import { humanize, label } from '../lib/labels'
import type { AuditEntry } from '../lib/types'
import { Tree } from '../components/Tree'
import { Card, Empty, Field, Loading, LoadError, Page } from '../components/ui'

const TYPES = ['ASSET', 'PARTY', 'SCHEMA', 'INSPECTION', 'FINDING', 'CORRECTIVE_ACTION', 'CERTIFICATE']

export function Audit() {
  const [params, setParams] = useSearchParams()
  const type = params.get('type') ?? ''
  const id = params.get('id') ?? ''
  const [draftId, setDraftId] = useState(id)
  const filtered = !!type && !!id
  const entries = useLoad(() => api.get<AuditEntry[]>('/audit' + (filtered ? query({ type, id }) : '')), [type, id])
  // Newest first reads better than the append order.
  const sorted = entries.data ? [...entries.data].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt)) : undefined

  return (
    <Page title="Auditoría" subtitle="Registro permanente de quién hizo qué y cuándo. No se puede modificar.">
      <Card>
        <form
          className="filters"
          style={{ marginBottom: 14 }}
          onSubmit={(e) => {
            e.preventDefault()
            setParams(type && draftId ? { type, id: draftId.trim() } : {})
          }}
        >
          <Field label="Elemento">
            <select value={type} onChange={(e) => setParams(e.target.value && draftId ? { type: e.target.value, id: draftId } : e.target.value ? { type: e.target.value } : {})}>
              <option value="">Todo el sistema</option>
              {TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}
            </select>
          </Field>
          <Field label="Identificador">
            <input value={draftId} onChange={(e) => setDraftId(e.target.value)} disabled={!type} />
          </Field>
          <button disabled={!type || !draftId.trim()}>Filtrar</button>
          {(type || id) && <button type="button" onClick={() => { setDraftId(''); setParams({}) }}>Limpiar</button>}
        </form>
        {entries.error && <LoadError message={entries.error} retry={entries.reload} />}
        {entries.loading && !sorted && <Loading />}
        {sorted &&
          (sorted.length === 0 ? (
            <Empty title="Sin registros" hint="Todavía no hay movimientos para mostrar." />
          ) : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Cuándo</th><th>Elemento</th><th>Acción</th><th>Detalle</th></tr></thead>
                <tbody>
                  {sorted.map((entry, index) => (
                    <tr key={index}>
                      <td className="nowrap">{formatInstant(entry.occurredAt)}</td>
                      <td>{label(entry.element.type)}<div className="mono muted">{entry.element.id}</div></td>
                      <td>{humanize(entry.action)}{entry.reason && <div className="muted small">{entry.reason}</div>}</td>
                      <td><Tree value={{ actor: entry.actor, detail: entry.detail }} /></td>
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
