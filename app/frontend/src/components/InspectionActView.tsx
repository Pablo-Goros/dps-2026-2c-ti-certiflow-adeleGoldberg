import { originLabel } from '../lib/assistant'
import { formatDate, formatInstant } from '../lib/format'
import { label } from '../lib/labels'
import type { ActObservation, CriterionResult, InspectionAct, InspectionActLine, ReportedTextValue } from '../lib/types'
import { useParties } from './Party'
import { Badge, Card, Empty, Facts, Loading, LoadError, ResultBadge, SeverityBadge } from './ui'
import { Tree } from './Tree'

/** The record of a closed inspection: what was answered, what each criterion resulted in and why. */
export function InspectionActView({ act, error, loading, retry }: {
  act: InspectionAct | undefined
  error: string | null
  loading: boolean
  retry: () => void
}) {
  const { nameOf } = useParties()
  if (error) return <Card title="Acta de inspección"><LoadError message={error} retry={retry} /></Card>
  if (loading && !act) return <Card title="Acta de inspección"><Loading /></Card>
  if (!act) return null

  return (
    <div id="acta">
      <Card title="Acta de inspección">
        <Facts
          items={[
            ['Activo al iniciar', act.asset?.name ?? 'Sin datos históricos'],
            ['Ubicación', act.asset?.location ?? '—'],
            ['Inspector', nameOf(act.inspector)],
            ['Versión aplicada', act.schemaVersionId ? `v${act.schemaVersionId.number}` : '—'],
            ['Prevista para', formatDate(act.expectedDate)],
            ['Cerrada', formatInstant(act.closedAt)],
          ]}
        />

        {act.sections.map((section) => (
          <details key={section.name} className="act-section" open>
            <summary><h3>{section.name}</h3></summary>
            {section.lines.length === 0 ? <Empty title="Sin criterios" /> : (
              <div className="table-wrap">
                <table className="act-table">
                  <thead><tr><th>Criterio</th><th>Respuesta</th><th>Resultado</th><th>Motivos</th><th>Evidencia</th></tr></thead>
                  <tbody>
                    {section.lines.map((line) => <ActLine key={line.criterionId} line={line} />)}
                  </tbody>
                </table>
              </div>
            )}
          </details>
        ))}

        <details className="act-section" open>
          <summary><h3>Rectificaciones</h3></summary>
          {act.rectifications.length === 0 ? <p className="muted">No hay rectificaciones.</p> : (
            <ul className="act-list">
              {act.rectifications.map((entry, index) => (
                <li key={entry.id ?? index}>
                  <strong>{entry.reason}</strong>
                  <span>{entry.changes.join('; ')}</span>
                  <span className="muted small">{nameOf(entry.author)} · {formatInstant(entry.performedAt)}</span>
                </li>
              ))}
            </ul>
          )}
        </details>

        {act.notes.length > 0 && (
          <details className="act-section" open>
            <summary><h3>Notas del acta</h3></summary>
            <ul className="act-list">
              {act.notes.map((note, index) => (
                <li key={index}>
                  <span>{note.criterionId && <strong>{note.criterionId}: </strong>}<ReportedValue value={note.text} /></span>
                  <span className="muted small">{nameOf(note.author)} · {formatInstant(note.recordedAt)}</span>
                </li>
              ))}
            </ul>
          </details>
        )}

        <details className="more" style={{ marginTop: 16 }}>
          <summary>Datos completos del acta</summary>
          <Tree value={act} />
        </details>
      </Card>
    </div>
  )
}

function ActLine({ line }: { line: InspectionActLine }) {
  return (
    <tr className={line.applicable ? '' : 'not-applicable'}>
      <td className="act-criterion">
        <strong>{line.criterionId}</strong>
        {!line.applicable && <div className="muted small">No aplicable</div>}
      </td>
      <td><ReportedValue value={line.answer} /></td>
      <td><ActResult line={line} /></td>
      <td>
        {line.reasons.length > 0 ? (
          <ul className="act-reasons">{line.reasons.map((reason, index) => <li key={index}>{reason}</li>)}</ul>
        ) : <span className="muted">—</span>}
        <ActObservations observations={line.observations ?? []} />
      </td>
      <td>
        {line.evidenceReferences.length
          ? line.evidenceReferences.map((value, index) => <div key={index} className="mono small"><ReportedValue value={value} /></div>)
          : <span className="muted">—</span>}
      </td>
    </tr>
  )
}

/** The result as a badge; a rectified one shows what it was before. */
function ActResult({ line }: { line: InspectionActLine }) {
  const result = line.result
  if (!result) return <span className="muted">—</span>
  const current = (result.type === 'Original' ? result.value : result.correctedValue) as CriterionResult | null
  return (
    <div className="act-result">
      {current && <ResultBadge result={current} />}
      <SeverityBadge severity={line.severity} />
      {result.type === 'Rectified' && (
        <span className="muted small">
          Antes: {label(result.originalValue)} · Rectificación: {result.reason}
        </span>
      )}
    </div>
  )
}

/** The non-conformities recorded on a line, saying which the assistant proposed and which the inspector wrote. */
function ActObservations({ observations }: { observations: ActObservation[] }) {
  if (observations.length === 0) return null
  return (
    <div className="act-observations">
      <div className="muted small">No conformidades observadas</div>
      <ul className="evidence-list">
        {observations.map((observation) => (
          <li key={observation.id}>
            <span>{label(observation.result)} · {label(observation.severity).toLowerCase()}: {observation.description}</span>
            <Badge tone={observation.origin ? 'info' : 'neutral'} icon={observation.origin ? 'bolt' : 'user'}>
              {originLabel(observation.origin)}
            </Badge>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Answers stored by the core in English ("yes"/"no") read in Spanish; everything else as it was recorded. */
function readable(value: string | null): string {
  if (value === null) return 'Sin respuesta'
  return ({ yes: 'Sí', no: 'No' } as Record<string, string>)[value] ?? value
}

function ReportedValue({ value }: { value: ReportedTextValue }) {
  if (value.type === 'Original') return <>{readable(value.value)}</>
  return (
    <span>
      <del>{readable(value.originalValue)}</del> → <strong>{readable(value.correctedValue)}</strong>
      <span className="muted small"> · Rectificación: {value.reason}</span>
    </span>
  )
}
