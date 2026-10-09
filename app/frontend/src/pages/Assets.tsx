import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, query } from '../lib/api'
import { useLoad } from '../lib/hooks'
import { label } from '../lib/labels'
import type { Asset, AssetType, AssetTypeInfo } from '../lib/types'
import { PartySelect } from '../components/Party'
import { Card, Empty, ErrorLine, Field, Loading, LoadError, Page, useAction } from '../components/ui'

export function Assets() {
  const [type, setType] = useState('')
  const [name, setName] = useState('')
  const [creating, setCreating] = useState(false)
  const meta = useLoad(() => api.get<AssetTypeInfo[]>('/meta/asset-types'), [])
  // The API takes one filter at a time (type first, then name), the same order used here.
  const assets = useLoad(() => api.get<Asset[]>('/assets' + query({ type, name: type ? undefined : name })), [type, name])

  return (
    <Page
      title="Activos"
      subtitle="Laboratorios, fábricas, instalaciones y equipos que se inspeccionan y certifican."
      actions={
        <button className="primary" onClick={() => setCreating((open) => !open)}>
          {creating ? 'Cerrar' : 'Registrar activo'}
        </button>
      }
    >
      {creating && meta.data && (
        <NewAsset
          types={meta.data}
          onDone={() => {
            setCreating(false)
            assets.reload()
          }}
        />
      )}
      <Card>
        <div className="filters" style={{ marginBottom: 14 }}>
          <Field label="Tipo">
            <select value={type} onChange={(e) => setType(e.target.value)}>
              <option value="">Todos</option>
              {meta.data?.map((info) => (
                <option key={info.name} value={info.name}>
                  {label(info.name)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Nombre contiene" hint={type ? 'Se ignora mientras haya un tipo elegido.' : undefined}>
            <input value={name} onChange={(e) => setName(e.target.value)} disabled={!!type} />
          </Field>
        </div>
        {assets.error && <LoadError message={assets.error} retry={assets.reload} />}
        {assets.loading && !assets.data && <Loading />}
        {assets.data &&
          (assets.data.length === 0 ? (
            <Empty title="No hay activos con esos filtros" hint="Registrá uno nuevo o cambiá los filtros." />
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Nombre</th>
                    <th>Tipo</th>
                    <th>Responsable</th>
                    <th>Ubicación</th>
                    <th>Jurisdicción</th>
                  </tr>
                </thead>
                <tbody>
                  {assets.data.map((asset) => (
                    <tr key={asset.id}>
                      <td>
                        <Link to={`/activos/${asset.id}`}>{asset.name}</Link>
                      </td>
                      <td>{label(asset.assetType)}</td>
                      <td>{asset.responsibleName}</td>
                      <td>{asset.location}</td>
                      <td>{asset.jurisdiction}</td>
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

function NewAsset({ types, onDone }: { types: AssetTypeInfo[]; onDone: () => void }) {
  const navigate = useNavigate()
  const jurisdictions = useLoad(() => api.get<string[]>('/meta/jurisdictions'), [])
  const [assetType, setAssetType] = useState<AssetType>(types[0].name)
  const [name, setName] = useState('')
  const [responsibleId, setResponsibleId] = useState('')
  const [location, setLocation] = useState('')
  const [jurisdiction, setJurisdiction] = useState('')
  const [values, setValues] = useState<Record<string, string>>({})
  const [subsystems, setSubsystems] = useState<string[] | null>(null)
  const { run, busy, error } = useAction()

  const info = types.find((t) => t.name === assetType)!
  const chosenSubsystems = subsystems ?? info.subsystems

  function changeType(next: AssetType) {
    setAssetType(next)
    setValues({})
    setSubsystems(null)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    const characteristics = Object.fromEntries(
      Object.entries(values).filter(([, value]) => value.trim() !== '').map(([key, value]) => [key, value.trim()]),
    )
    let created: Asset | undefined
    const ok = await run(async () => {
      created = await api.post<Asset>('/assets', {
        name: name.trim(),
        assetType,
        responsibleId,
        location: location.trim(),
        characteristics,
        jurisdiction: jurisdiction || jurisdictions.data?.[0],
        subsystems: info.subsystems.length ? chosenSubsystems : undefined,
      })
    }, 'Activo registrado')
    if (ok && created) {
      onDone()
      navigate(`/activos/${created.id}`)
    }
  }

  return (
    <Card title="Registrar activo">
      <form className="form" onSubmit={submit}>
        <Field label="Nombre">
          <input value={name} onChange={(e) => setName(e.target.value)} required />
        </Field>
        <Field label="Tipo">
          <select value={assetType} onChange={(e) => changeType(e.target.value as AssetType)}>
            {types.map((t) => (
              <option key={t.name} value={t.name}>
                {label(t.name)}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Responsable">
          <PartySelect value={responsibleId} onChange={setResponsibleId} />
        </Field>
        <Field label="Ubicación">
          <input value={location} onChange={(e) => setLocation(e.target.value)} required />
        </Field>
        <Field label="Jurisdicción" hint="Define la política de certificación que se aplica.">
          <select value={jurisdiction || jurisdictions.data?.[0] || ''} onChange={(e) => setJurisdiction(e.target.value)}>
            {jurisdictions.data?.map((j) => (
              <option key={j}>{j}</option>
            ))}
          </select>
        </Field>
        {info.characteristics.map((characteristic) => (
          <Field key={characteristic} label={label(characteristic)}>
            <input
              value={values[characteristic] ?? ''}
              onChange={(e) => setValues({ ...values, [characteristic]: e.target.value })}
            />
          </Field>
        ))}
        {info.subsystems.length > 0 && (
          <Field group label="Subsistemas que se certifican por separado" wide>
            <div className="checks">
              {info.subsystems.map((subsystem) => (
                <label key={subsystem}>
                  <input
                    type="checkbox"
                    checked={chosenSubsystems.includes(subsystem)}
                    onChange={(e) =>
                      setSubsystems(
                        e.target.checked
                          ? info.subsystems.filter((s) => s === subsystem || chosenSubsystems.includes(s))
                          : chosenSubsystems.filter((s) => s !== subsystem),
                      )
                    }
                  />
                  {label(subsystem)}
                </label>
              ))}
            </div>
          </Field>
        )}
        <div className="form-actions">
          <button className="primary" disabled={busy || !responsibleId}>
            Registrar activo
          </button>
        </div>
      </form>
      <ErrorLine message={error} />
    </Card>
  )
}
