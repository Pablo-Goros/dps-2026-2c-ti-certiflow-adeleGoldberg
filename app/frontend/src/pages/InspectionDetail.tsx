import { useMemo, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { formatDate, formatInstant } from '../lib/format'
import { useLoad } from '../lib/hooks'
import { criteriaById } from '../lib/rules'
import type { Asset, Inspection, Schema } from '../lib/types'
import { CertificationPanel } from '../components/CertificationPanel'
import { CriterionCard } from '../components/CriterionCard'
import { useParties } from '../components/Party'
import {
  Card, Empty, ErrorLine, Facts, Field, InspectionBadge, Loading, LoadError, Notice, Page, useAction,
} from '../components/ui'

export function InspectionDetail() {
  const { id = '' } = useParams()
  const { nameOf } = useParties()
  const inspection = useLoad(() => api.get<Inspection>(`/inspections/${id}`), [id])
  const asset = useLoad(
    () => (inspection.data ? api.get<Asset>(`/assets/${inspection.data.assetId}`) : Promise.resolve(undefined)),
    [inspection.data?.assetId],
  )
  const schemas = useLoad(
    () => (inspection.data?.schemaVersion ? api.get<Schema[]>('/schemas') : Promise.resolve([] as Schema[])),
    [inspection.data?.schemaVersion],
  )
  const { run, busy, error } = useAction()

  const version = useMemo(
    () => schemas.data?.flatMap((s) => s.versions).find((v) => v.id === inspection.data?.schemaVersion),
    [schemas.data, inspection.data?.schemaVersion],
  )
  const criteria = useMemo(() => criteriaById(version), [version])

  if (inspection.error) return <Page title="Inspección"><LoadError message={inspection.error} retry={inspection.reload} /></Page>
  if (!inspection.data) return <Page title="Inspección"><Loading /></Page>
  const i = inspection.data
  const assetName = asset.data?.name ?? i.assetId
  const sections = [...new Set(i.criteria.map((line) => line.section))]

  const start = () => run(async () => { await api.post(`/inspections/${id}/start`); inspection.reload() }, 'Inspección iniciada')
  const close = () => run(async () => { await api.post(`/inspections/${id}/close`); inspection.reload() }, 'Inspección cerrada')
  const answered = i.criteria.filter((line) => line.applicable && line.answer).length
  const applicable = i.criteria.filter((line) => line.applicable).length

  return (
    <Page
      title={`Inspección de ${assetName}`}
      subtitle={<><InspectionBadge status={i.status} /> · prevista para el {formatDate(i.expectedDate)}</>}
      actions={
        <>
          <Link className="button" to={`/activos/${i.assetId}`}>Ver activo</Link>
          {i.status === 'CLOSED' && <Link className="button" to={`/hallazgos?inspectionId=${i.id}`}>Ver hallazgos</Link>}
        </>
      }
    >
      <Card>
        <Facts
          items={[
            ['Inspector', nameOf(i.inspectorId)],
            ['Inicio', formatInstant(i.startedAt)],
            ['Cierre', formatInstant(i.closedAt)],
            ['Versión del esquema', i.schemaVersion ?? 'Se fija al iniciar'],
          ]}
        />
      </Card>
      <ErrorLine message={error} />

      {i.status === 'ASSIGNED' && (
        <Card title="Todavía no empezó">
          <p className="muted">
            Al iniciar se congela la versión vigente del esquema de inspección: aunque se publique otra después, esta inspección
            se completa con la misma.
          </p>
          <button className="primary" disabled={busy} onClick={start}>Iniciar inspección</button>
        </Card>
      )}

      {i.status !== 'ASSIGNED' && (
        <>
          {i.status === 'IN_PROGRESS' && !version && schemas.data && (
            <Notice kind="warn">No se encontró la versión del esquema de esta inspección; se muestran las respuestas sin poder editarlas.</Notice>
          )}
          {sections.map((section) => (
            <Card key={section} title={section}>
              <div className="stack">
                {i.criteria.filter((line) => line.section === section).map((line) => (
                  <CriterionCard
                    key={line.criterionId}
                    inspectionId={i.id}
                    line={line}
                    criterion={criteria.get(line.criterionId)}
                    editable={i.status === 'IN_PROGRESS'}
                    onChange={inspection.reload}
                  />
                ))}
              </div>
            </Card>
          ))}
          <Notes inspection={i} onChange={inspection.reload} />
        </>
      )}

      {i.status === 'IN_PROGRESS' && (
        <Card title="Cerrar inspección">
          <p className="muted">
            Respondiste {answered} de {applicable} criterios. Al cerrar se evalúan las respuestas, se generan los hallazgos y la
            inspección ya no se puede editar.
          </p>
          <button className="primary" disabled={busy || answered === 0} onClick={close}>Cerrar inspección</button>
        </Card>
      )}

      {i.status === 'CLOSED' && (
        <CertificationPanel inspectionId={i.id} assetId={i.assetId} subsystems={i.subsystems} />
      )}
    </Page>
  )
}

function Notes({ inspection, onChange }: { inspection: Inspection; onChange: () => void }) {
  const { nameOf } = useParties()
  const { run, busy, error } = useAction()
  const [text, setText] = useState('')
  const [criterionId, setCriterionId] = useState('')
  const open = inspection.status === 'IN_PROGRESS'

  async function submit(event: FormEvent) {
    event.preventDefault()
    const ok = await run(async () => {
      await api.post(`/inspections/${inspection.id}/notes`, { criterionId: criterionId || null, text: text.trim() })
      onChange()
    }, 'Nota agregada')
    if (ok) setText('')
  }
  const remove = (noteId: string) =>
    run(async () => {
      await api.del(`/inspections/${inspection.id}/notes/${encodeURIComponent(noteId)}`)
      onChange()
    })

  if (!open && inspection.notes.length === 0) return null
  return (
    <Card title="Notas">
      {inspection.notes.length === 0 ? (
        <Empty title="Sin notas" hint="Agregá observaciones generales o sobre un criterio." />
      ) : (
        <ul className="evidence-list">
          {inspection.notes.map((note) => (
            <li key={note.id}>
              <span>{note.text}</span>
              <span className="muted small">
                {note.criterionId ? `sobre ${note.criterionId} · ` : ''}{nameOf(note.authorId)} · {formatInstant(note.recordedAt)}
              </span>
              {open && <button className="small danger" disabled={busy} onClick={() => remove(note.id)}>Quitar</button>}
            </li>
          ))}
        </ul>
      )}
      {open && (
        <form className="form" style={{ marginTop: 14 }} onSubmit={submit}>
          <Field label="Nota" wide>
            <textarea value={text} onChange={(e) => setText(e.target.value)} />
          </Field>
          <Field label="Sobre">
            <select value={criterionId} onChange={(e) => setCriterionId(e.target.value)}>
              <option value="">La inspección en general</option>
              {inspection.criteria.filter((l) => l.applicable).map((l) => <option key={l.criterionId} value={l.criterionId}>{l.criterionId}</option>)}
            </select>
          </Field>
          <div className="form-actions">
            <button disabled={busy || !text.trim()}>Agregar nota</button>
          </div>
        </form>
      )}
      <ErrorLine message={error} />
    </Card>
  )
}
