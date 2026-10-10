import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, ApiError } from '../lib/api'
import { formatInstant, isFuture, localDateTimeToInstant } from '../lib/format'
import { useLoad } from '../lib/hooks'
import { label } from '../lib/labels'
import { describeRule } from '../lib/rules'
import type { AssetType, AssetTypeInfo, Schema, SchemaVersion, Section } from '../lib/types'
import { SectionEditor } from '../components/SectionEditor'
import { Badge, Card, ErrorLine, Field, Loading, LoadError, Notice, Page, useAction } from '../components/ui'

export function SchemaDetail() {
  const { id = '' } = useParams()
  const schema = useLoad(() => api.get<Schema>(`/schemas/${id}`), [id])
  const types = useLoad(() => api.get<AssetTypeInfo[]>('/meta/asset-types'), [])
  const { run, busy, error } = useAction()
  const [adding, setAdding] = useState(false)
  const [effectiveFrom, setEffectiveFrom] = useState('')
  const [violations, setViolations] = useState<string[]>([])

  if (schema.error) return <Page title="Esquema"><LoadError message={schema.error} retry={schema.reload} /></Page>
  if (!schema.data || !types.data) return <Page title="Esquema"><Loading /></Page>
  const s = schema.data
  const subsystems = [...new Set(types.data.filter((t) => s.assetTypes.includes(t.name)).flatMap((t) => t.subsystems))]
  const free = types.data.filter((t) => !s.assetTypes.includes(t.name))
  const draftSections = s.draft?.sections ?? []

  const change = (work: () => Promise<unknown>, done: string) =>
    run(async () => { await work(); schema.reload() }, done)

  async function publish() {
    setViolations([])
    const scheduled = localDateTimeToInstant(effectiveFrom)
    if (effectiveFrom && (!scheduled || !isFuture(scheduled))) {
      setViolations(['La fecha de vigencia tiene que ser futura. Dejala vacía para publicar desde ahora.'])
      return
    }
    try {
      await run(async () => {
        try {
          await api.post(`/schemas/${id}/publish`, scheduled ? { effectiveFrom: scheduled } : {})
        } catch (failure) {
          if (failure instanceof ApiError && failure.code === 'SCHEMA_NOT_PUBLISHABLE') setViolations(failure.details)
          throw failure
        }
        setEffectiveFrom('')
        schema.reload()
      }, scheduled ? 'Versión programada' : 'Versión publicada')
    } catch {
      // run already reports the failure
    }
  }

  return (
    <Page title={s.name} subtitle={s.effectiveVersion ? `Versión vigente: v${s.effectiveVersion}` : 'Todavía no tiene una versión vigente'}>
      <ErrorLine message={error} />

      <Card title="Se aplica a">
        <div className="row">
          {s.assetTypes.length === 0 && <span className="muted">Ningún tipo de activo.</span>}
          {s.assetTypes.map((type: AssetType) => (
            <span key={type} className="row" style={{ gap: 4 }}>
              <Badge tone="seal">{label(type)}</Badge>
              <button className="small" disabled={busy} aria-label={`Dejar de aplicar a ${label(type)}`}
                onClick={() => change(() => api.del(`/schemas/${id}/applicability/${type}`), 'Aplicabilidad actualizada')}>×</button>
            </span>
          ))}
          {free.length > 0 && (
            <select aria-label="Agregar tipo" style={{ width: 'auto' }} value="" disabled={busy}
              onChange={(e) => e.target.value && change(() => api.post(`/schemas/${id}/applicability`, { assetType: e.target.value }), 'Aplicabilidad actualizada')}>
              <option value="">Agregar tipo…</option>
              {free.map((t) => <option key={t.name} value={t.name}>{label(t.name)}</option>)}
            </select>
          )}
        </div>
      </Card>

      <Card
        title="Borrador"
        actions={s.draft ? (
          <button className="danger" disabled={busy} onClick={() => change(() => api.del(`/schemas/${id}/draft`), 'Borrador descartado')}>Descartar borrador</button>
        ) : null}
      >
        {!s.draft ? (
          <>
            <p className="muted">Para cambiar lo que se revisa, abrí un borrador, armá las secciones y publicalo como una versión nueva.</p>
            <button className="primary" disabled={busy} onClick={() => change(() => api.post(`/schemas/${id}/draft`), 'Borrador abierto')}>Abrir borrador</button>
          </>
        ) : (
          <div className="stack">
            {draftSections.length === 0 && <p className="muted" style={{ margin: 0 }}>El borrador está vacío. Agregá al menos una sección.</p>}
            {draftSections.map((section) => (
              <SectionView key={section.name} section={section}
                onRemove={() => change(() => api.del(`/schemas/${id}/draft/sections/${encodeURIComponent(section.name)}`), 'Sección quitada')} busy={busy} />
            ))}
            {!adding && <div><button onClick={() => setAdding(true)}>Agregar sección</button></div>}
          </div>
        )}
      </Card>

      {s.draft && adding && (
        <SectionEditor
          subsystems={subsystems}
          nextOrder={draftSections.length + 1}
          onCancel={() => setAdding(false)}
          onAdd={async (section) => run(async () => { await api.post(`/schemas/${id}/draft/sections`, section); schema.reload() }, 'Sección agregada')}
        />
      )}

      {s.draft && !adding && (
        <Card title="Publicar">
          <div className="form">
            <Field label="Vigente desde" hint="Vacío = desde ahora. Con una fecha futura, la versión queda programada y la actual sigue rigiendo hasta entonces.">
              <input type="datetime-local" value={effectiveFrom} onChange={(e) => setEffectiveFrom(e.target.value)} />
            </Field>
            <div className="form-actions">
              <button className="primary" disabled={busy || draftSections.length === 0} onClick={publish}>Publicar versión</button>
            </div>
          </div>
          {violations.length > 0 && (
            <div style={{ marginTop: 14 }}>
              <Notice kind="error">
                No se puede publicar todavía:
                <ul>{violations.map((v, i) => <li key={i}>{v}</li>)}</ul>
              </Notice>
            </div>
          )}
        </Card>
      )}

      {s.versions.length > 0 && <VersionAtDate schemaId={s.id} />}

      <Card title="Versiones publicadas">
        {s.versions.length === 0 ? <p className="muted" style={{ margin: 0 }}>Todavía no se publicó ninguna versión.</p> : (
          <div className="stack">
            {[...s.versions].reverse().map((version) => (
              <details key={version.id}>
                <summary>
                  <strong>v{version.number}</strong>{' '}
                  <span className="muted">publicada {formatInstant(version.publishedAt)} · vigente desde {formatInstant(version.effectiveFrom)}</span>{' '}
                  {version.number === s.effectiveVersion && <Badge tone="ok">Vigente</Badge>}
                  {isFuture(version.effectiveFrom) && <Badge tone="info">Programada</Badge>}
                </summary>
                <div className="stack" style={{ marginTop: 10 }}>
                  {version.sections.map((section) => <SectionView key={section.name} section={section} />)}
                </div>
              </details>
            ))}
          </div>
        )}
      </Card>
    </Page>
  )
}

/** F2: which version was (or will be) in force on a given date. */
function VersionAtDate({ schemaId }: { schemaId: string }) {
  const [at, setAt] = useState('')
  const [found, setFound] = useState<SchemaVersion | null>(null)
  const [answer, setAnswer] = useState<string | null>(null)
  const [failure, setFailure] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function consult() {
    const instant = localDateTimeToInstant(at)
    if (!instant) {
      setFailure('Elegí una fecha y hora.')
      return
    }
    setBusy(true)
    setFailure(null)
    setFound(null)
    setAnswer(null)
    try {
      const version = await api.get<SchemaVersion>(`/schemas/${schemaId}/effective-version?at=${encodeURIComponent(instant)}`)
      setFound(version)
      setAnswer(`En esa fecha regía la versión v${version.number} (vigente desde ${formatInstant(version.effectiveFrom)}).`)
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) setAnswer('En esa fecha todavía no regía ninguna versión.')
      else setFailure(e instanceof ApiError ? e.message : 'No se pudo consultar.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card title="¿Qué versión regía en una fecha?">
      <div className="form">
        <Field label="Fecha y hora" hint="Sirve para el pasado y para el futuro: una versión programada rige recién desde su fecha de vigencia.">
          <input type="datetime-local" value={at} onChange={(e) => setAt(e.target.value)} />
        </Field>
        <div className="form-actions">
          <button disabled={busy} onClick={consult}>Consultar</button>
        </div>
      </div>
      <ErrorLine message={failure} />
      {answer && <div style={{ marginTop: 14 }}><Notice kind={found ? 'ok' : 'info'}>{answer}</Notice></div>}
      {found && (
        <div className="stack" style={{ marginTop: 10 }}>
          {found.sections.map((section) => <SectionView key={section.name} section={section} />)}
        </div>
      )}
    </Card>
  )
}

function SectionView({ section, onRemove, busy }: { section: Section; onRemove?: () => void; busy?: boolean }) {
  return (
    <div className="criterion">
      <header>
        <strong>{section.order}. {section.name}</strong>
        {onRemove && <button className="small danger" disabled={busy} onClick={onRemove}>Quitar sección</button>}
      </header>
      <ul className="tree" style={{ margin: 0 }}>
        {section.criteria.map((criterion) => (
          <li key={criterion.id}>
            <strong>{criterion.id}</strong> — {describeRule(criterion.rule)}
            {criterion.subsystem && <> <Badge tone="seal">{label(criterion.subsystem)}</Badge></>}
            {criterion.evidence.length > 0 && <span className="muted"> · pide {criterion.evidence.map((e) => e.label).join(', ')}</span>}
          </li>
        ))}
      </ul>
    </div>
  )
}
