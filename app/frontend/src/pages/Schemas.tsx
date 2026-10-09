import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { isFuture } from '../lib/format'
import { useLoad } from '../lib/hooks'
import { label } from '../lib/labels'
import type { AssetType, AssetTypeInfo, Schema } from '../lib/types'
import { Badge, Card, Empty, ErrorLine, Field, Loading, LoadError, Page, useAction } from '../components/ui'

export function Schemas() {
  const schemas = useLoad(() => api.get<Schema[]>('/schemas'), [])
  const types = useLoad(() => api.get<AssetTypeInfo[]>('/meta/asset-types'), [])
  const [creating, setCreating] = useState(false)

  return (
    <Page
      title="Esquemas de inspección"
      subtitle="Qué se revisa en cada tipo de activo. Cada publicación crea una versión nueva; las inspecciones usan la que estaba vigente al iniciar."
      actions={<button className="primary" onClick={() => setCreating((open) => !open)}>{creating ? 'Cerrar' : 'Crear esquema'}</button>}
    >
      {creating && types.data && <NewSchema types={types.data} />}
      <Card>
        {schemas.error && <LoadError message={schemas.error} retry={schemas.reload} />}
        {schemas.loading && !schemas.data && <Loading />}
        {schemas.data &&
          (schemas.data.length === 0 ? (
            <Empty title="Todavía no hay esquemas" hint="Creá uno, agregale secciones y publicalo para poder iniciar inspecciones." />
          ) : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Nombre</th><th>Se aplica a</th><th>Versión vigente</th><th>Borrador</th></tr></thead>
                <tbody>
                  {schemas.data.map((schema) => {
                    const scheduled = schema.versions.filter((v) => isFuture(v.effectiveFrom))
                    return (
                      <tr key={schema.id}>
                        <td><Link to={`/esquemas/${schema.id}`}>{schema.name}</Link></td>
                        <td>{schema.assetTypes.map(label).join(', ') || '—'}</td>
                        <td>
                          {schema.effectiveVersion ? `v${schema.effectiveVersion}` : <span className="muted">Ninguna</span>}
                          {scheduled.length > 0 && <> <Badge tone="info">v{scheduled[0].number} programada</Badge></>}
                        </td>
                        <td>{schema.draft ? <Badge tone="warn">Abierto</Badge> : '—'}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          ))}
      </Card>
    </Page>
  )
}

function NewSchema({ types }: { types: AssetTypeInfo[] }) {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [chosen, setChosen] = useState<AssetType[]>([])
  const { run, busy, error } = useAction()

  async function submit(event: FormEvent) {
    event.preventDefault()
    let created: Schema | undefined
    const ok = await run(async () => {
      created = await api.post<Schema>('/schemas', { name: name.trim(), assetTypes: chosen })
    }, 'Esquema creado')
    if (ok && created) navigate(`/esquemas/${created.id}`)
  }

  return (
    <Card title="Crear esquema">
      <form className="form" onSubmit={submit}>
        <Field label="Nombre"><input value={name} onChange={(e) => setName(e.target.value)} required /></Field>
        <Field group label="Tipos de activo a los que se aplica" hint="Cada tipo de activo puede tener un solo esquema." wide>
          <div className="checks">
            {types.map((t) => (
              <label key={t.name}>
                <input
                  type="checkbox"
                  checked={chosen.includes(t.name)}
                  onChange={(e) => setChosen(e.target.checked ? [...chosen, t.name] : chosen.filter((c) => c !== t.name))}
                />
                {label(t.name)}
              </label>
            ))}
          </div>
        </Field>
        <div className="form-actions"><button className="primary" disabled={busy || !name.trim() || chosen.length === 0}>Crear esquema</button></div>
      </form>
      <ErrorLine message={error} />
    </Card>
  )
}
