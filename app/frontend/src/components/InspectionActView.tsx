import { formatDate, formatInstant } from '../lib/format'
import type { InspectionAct, ReportedTextValue } from '../lib/types'
import { Card, Empty, Loading, LoadError } from './ui'
import { Tree } from './Tree'

export function InspectionActView({ act, error, loading, retry }: {
  act: InspectionAct | undefined
  error: string | null
  loading: boolean
  retry: () => void
}) {
  if (error) return <Card title="Acta de inspección"><LoadError message={error} retry={retry} /></Card>
  if (loading && !act) return <Card title="Acta de inspección"><Loading /></Card>
  if (!act) return null

  return (
    <div id="acta">
      <Card title="Acta de inspección">
        <p className="muted">
          Activo al iniciar: <strong>{act.asset?.name ?? 'Sin datos históricos'}</strong>
          {act.asset?.location ? ` · ${act.asset.location}` : ''}. Versión aplicada: {act.schemaVersionId ?? '—'}.
        </p>
        <p className="muted">Inspector: {act.inspector} · prevista para el {formatDate(act.expectedDate)} · cerrada el {formatInstant(act.closedAt)}.</p>
        {act.sections.map((section) => (
          <section key={section.name}>
            <h3>{section.name}</h3>
            {section.lines.length === 0 ? <Empty title="Sin criterios" /> : (
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Criterio</th><th>Respuesta</th><th>Resultado</th><th>Motivos</th><th>Evidencia</th></tr></thead>
                  <tbody>
                    {section.lines.map((line) => (
                      <tr key={line.criterionId}>
                        <td>{line.criterionId}{!line.applicable && <div className="muted small">No aplicable</div>}</td>
                        <td><ReportedValue value={line.answer} /></td>
                        <td>{line.result ? <ReportedValue value={line.result} /> : '—'}</td>
                        <td>{line.reasons.length ? <ul>{line.reasons.map((reason, index) => <li key={index}>{reason}</li>)}</ul> : '—'}</td>
                        <td>{line.evidenceReferences.length ? line.evidenceReferences.map((value, index) => <div key={index}><ReportedValue value={value} /></div>) : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        ))}
        <h3>Rectificaciones</h3>
        {act.rectifications.length === 0 ? <p className="muted">No hay rectificaciones.</p> : (
          <ul className="evidence-list">
            {act.rectifications.map((entry, index) => (
              <li key={entry.id ?? index}>
                <span><strong>{entry.reason}</strong><br />{entry.changes.join('; ')}</span>
                <span className="muted small">{entry.author} · {formatInstant(entry.performedAt)}</span>
              </li>
            ))}
          </ul>
        )}
        {act.notes.length > 0 && (
          <>
            <h3>Notas del acta</h3>
            <ul className="evidence-list">
              {act.notes.map((note, index) => (
                <li key={index}>
                  <span>{note.criterionId ? `${note.criterionId}: ` : ''}<ReportedValue value={note.text} /></span>
                  <span className="muted small">{note.author} · {formatInstant(note.recordedAt)}</span>
                </li>
              ))}
            </ul>
          </>
        )}
        <details style={{ marginTop: 16 }}>
          <summary>Datos completos del acta</summary>
          <Tree value={act} />
        </details>
      </Card>
    </div>
  )
}

function ReportedValue({ value }: { value: ReportedTextValue }) {
  if (value.type === 'Original') return <>{value.value ?? 'Sin respuesta'}</>
  return (
    <span>
      <del>{value.originalValue ?? 'Sin respuesta'}</del> → <strong>{value.correctedValue ?? 'Sin respuesta'}</strong>
      <span className="muted small"> · Rectificación: {value.reason}</span>
    </span>
  )
}
