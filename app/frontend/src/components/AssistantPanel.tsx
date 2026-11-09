import { useState, type FormEvent } from 'react'
import { api, describeError } from '../lib/api'
import { editFormOf, editRequest, unavailableMessage, type EditForm } from '../lib/assistant'
import { formatInstant } from '../lib/format'
import { useLoad } from '../lib/hooks'
import { label } from '../lib/labels'
import type {
  AssistantInfo, Consultation, CriterionResult, Inspection, Severity, Suggestion, SuggestionDecisionView, Verdict,
} from '../lib/types'
import { Icon } from './Icon'
import { useParties } from './Party'
import {
  Badge, Card, Empty, ErrorLine, Field, Loading, LoadError, Notice, ResultBadge, SeverityBadge, useAction, useToast,
  type Tone,
} from './ui'

const NON_CONFORMING: CriterionResult[] = ['OBSERVED', 'REJECTED']
const SEVERITIES: Severity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const verdictTone: Record<Verdict, Tone> = { ACCEPTED: 'ok', EDITED: 'info', DISCARDED: 'neutral' }

const ASSISTANT_TITLE = (
  <>
    <Icon name="bolt" size={18} className="assistant-icon" /> Asistente de inspección{' '}
    <Badge tone="info">IA</Badge>
  </>
)

/**
 * The inspection assistant: ask it for suggestions, then accept, edit or discard each one. Nothing
 * reaches the inspection until the inspector decides, and the history keeps what was suggested.
 */
export function AssistantPanel({ inspection, onChange }: { inspection: Inspection; onChange: () => void }) {
  const toast = useToast()
  const open = inspection.status === 'IN_PROGRESS'
  const info = useLoad(() => api.get<AssistantInfo>('/meta/assistant'), [])
  const enabled = info.data?.enabled === true
  const consultations = useLoad(
    () => (enabled ? api.get<Consultation[]>(`/inspections/${inspection.id}/consultations`) : Promise.resolve([] as Consultation[])),
    [inspection.id, enabled],
  )
  const [consulting, setConsulting] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  const history = consultations.data ?? []
  if (!open && history.length === 0) return null
  if (info.error) return <Card title={ASSISTANT_TITLE} className="assistant-card"><LoadError message={info.error} retry={info.reload} /></Card>
  if (!info.data) return <Card title={ASSISTANT_TITLE} className="assistant-card"><Loading /></Card>

  if (!enabled) {
    return (
      <Card title={ASSISTANT_TITLE} className="assistant-card">
        <Notice>
          El asistente está apagado en este servidor. Se enciende con <code>certiflow.assistant.enabled=true</code> y un
          proveedor configurado.
        </Notice>
      </Card>
    )
  }

  const withoutProvider = info.data.provider === 'none'
  const hasNotes = inspection.notes.length > 0

  async function consult() {
    setFailure(null)
    setConsulting(true)
    try {
      await api.post(`/inspections/${inspection.id}/consultations`)
      consultations.reload()
      toast.show('El asistente respondió')
    } catch (error) {
      setFailure(unavailableMessage(error) ?? describeError(error))
    } finally {
      setConsulting(false)
    }
  }

  const decided = () => {
    consultations.reload()
    onChange()
  }
  const [latest, ...earlier] = history

  return (
    <Card title={ASSISTANT_TITLE} className="assistant-card">
      <p className="muted">
        Lee tus notas y la evidencia, y sugiere hallazgos que la evaluación de las reglas no refleja. Las sugerencias no
        cambian nada hasta que las aceptás o las editás; el asistente nunca cierra la inspección ni emite certificados.
      </p>
      {open && withoutProvider && (
        <Notice kind="warn">El asistente está encendido pero no tiene un proveedor de modelo configurado en el servidor.</Notice>
      )}
      {open && (
        <div className="row" style={{ margin: '10px 0' }}>
          <button className="primary" disabled={consulting || !hasNotes || withoutProvider} onClick={consult}>
            Pedir sugerencias
          </button>
          {!hasNotes && <span className="muted small">Agregá al menos una nota: es lo que lee el asistente.</span>}
        </div>
      )}
      {consulting && <Loading what="El asistente está leyendo las notas y la evidencia…" />}
      {failure && <Notice kind="error">{failure}</Notice>}
      {consultations.error && <LoadError message={consultations.error} retry={consultations.reload} />}

      {latest && (
        <ConsultationView consultation={latest} inspection={inspection} open={open} onDecided={decided} />
      )}
      {earlier.length > 0 && (
        <details className="more">
          <summary>Consultas anteriores ({earlier.length})</summary>
          <div className="stack">
            {earlier.map((consultation) => (
              <ConsultationView key={consultation.id} consultation={consultation} inspection={inspection} open={open} onDecided={decided} />
            ))}
          </div>
        </details>
      )}
    </Card>
  )
}

function ConsultationView({ consultation, inspection, open, onDecided }: {
  consultation: Consultation
  inspection: Inspection
  open: boolean
  onDecided: () => void
}) {
  const { nameOf } = useParties()
  return (
    <section className="consultation" aria-label={`Consulta ${consultation.id}`}>
      <div className="consultation-meta">
        <span className="muted small">
          Consulta del {formatInstant(consultation.requestedAt)} · pedida por {nameOf(consultation.requestedBy)}
        </span>
        <details className="tech">
          <summary>Detalles técnicos</summary>
          <span className="muted small">
            Modelo <span className="mono">{consultation.assistant.model}</span> · instrucciones{' '}
            <span className="mono">{consultation.assistant.prompt}</span>. Los configura el servidor; quedan
            registrados para saber qué produjo cada sugerencia.
          </span>
        </details>
      </div>
      {consultation.suggestions.length === 0 ? (
        <Empty title="Sin sugerencias" hint="El asistente no encontró problemas que las reglas no reflejen." />
      ) : (
        <div className="stack">
          {consultation.suggestions.map((suggestion) => (
            <SuggestionCard
              key={suggestion.id}
              consultationId={consultation.id}
              suggestion={suggestion}
              inspection={inspection}
              open={open}
              onDecided={onDecided}
            />
          ))}
        </div>
      )}
      {consultation.rejected.length > 0 && (
        <details className="more">
          <summary>Respuestas descartadas por el sistema ({consultation.rejected.length})</summary>
          <ul className="evidence-list">
            {consultation.rejected.map((rejected, index) => (
              <li key={index}>
                <strong>{rejected.criterionId}</strong>
                <span>{rejected.description}</span>
                <span className="muted small">· {rejected.reason}</span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  )
}

type Mode = 'idle' | 'edit' | 'discard'

function SuggestionCard({ consultationId, suggestion, inspection, open, onDecided }: {
  consultationId: string
  suggestion: Suggestion
  inspection: Inspection
  open: boolean
  onDecided: () => void
}) {
  const { run, busy, error } = useAction()
  const [mode, setMode] = useState<Mode>('idle')
  const [form, setForm] = useState<EditForm>(() => editFormOf(suggestion))
  const [reason, setReason] = useState('')
  const path = `/inspections/${inspection.id}/consultations/${encodeURIComponent(consultationId)}`
    + `/suggestions/${encodeURIComponent(suggestion.id)}/decision`
  const decide = (body: unknown, done: string) =>
    run(async () => {
      await api.post(path, body)
      setMode('idle')
      onDecided()
    }, done)

  const edit = editRequest(form)
  const submitEdit = (event: FormEvent) => {
    event.preventDefault()
    if ('request' in edit) decide(edit.request, 'Sugerencia editada y registrada')
  }
  const submitDiscard = (event: FormEvent) => {
    event.preventDefault()
    decide({ verdict: 'DISCARDED', reason: reason.trim() || null }, 'Sugerencia descartada')
  }
  const applicable = inspection.criteria.filter((line) => line.applicable)

  return (
    <article className={`suggestion ${suggestion.decision ? 'decided' : ''}`} aria-label={`Sugerencia sobre ${suggestion.criterionId}`}>
      <header className="suggestion-head">
        <div>
          <Badge tone="info" icon="bolt">Sugerencia de IA</Badge>{' '}
          <strong>{suggestion.criterionId}</strong>
          {suggestion.section && <span className="muted small"> · {suggestion.section}</span>}
        </div>
        <div className="row">
          <ResultBadge result={suggestion.result} />
          <SeverityBadge severity={suggestion.severity} />
        </div>
      </header>
      <p style={{ margin: '6px 0 0' }}>{suggestion.description}</p>
      <p className="why"><span className="muted">Por qué lo sugiere: </span>{suggestion.rationale}</p>

      {suggestion.decision ? (
        <DecisionView decision={suggestion.decision} />
      ) : open && mode === 'idle' ? (
        <div className="row" style={{ marginTop: 10 }}>
          <button className="small primary" disabled={busy} onClick={() => decide({ verdict: 'ACCEPTED' }, 'Sugerencia aceptada y registrada')}>
            Aceptar
          </button>
          <button className="small" disabled={busy} onClick={() => setMode('edit')}>Editar</button>
          <button className="small danger" disabled={busy} onClick={() => setMode('discard')}>Descartar</button>
        </div>
      ) : !open ? (
        <p className="muted small" style={{ marginTop: 8 }}>Sin decidir: la inspección ya no está en curso.</p>
      ) : null}

      {open && !suggestion.decision && mode === 'edit' && (
        <form className="form" style={{ marginTop: 12 }} onSubmit={submitEdit} aria-label="Editar sugerencia">
          <Field label="Criterio">
            <select value={form.criterionId} onChange={(e) => setForm({ ...form, criterionId: e.target.value })}>
              {applicable.map((line) => <option key={line.criterionId} value={line.criterionId}>{line.criterionId}</option>)}
            </select>
          </Field>
          <Field label="Resultado">
            <select value={form.result} onChange={(e) => setForm({ ...form, result: e.target.value as CriterionResult })}>
              {NON_CONFORMING.map((result) => <option key={result} value={result}>{label(result)}</option>)}
            </select>
          </Field>
          <Field label="Severidad">
            <select value={form.severity} onChange={(e) => setForm({ ...form, severity: e.target.value as Severity })}>
              {SEVERITIES.map((severity) => <option key={severity} value={severity}>{label(severity)}</option>)}
            </select>
          </Field>
          <Field label="Descripción" wide>
            <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </Field>
          <Field label="Por qué la editás" wide hint="Queda registrado junto a la sugerencia original.">
            <textarea value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} />
          </Field>
          <div className="form-actions">
            <button className="primary" disabled={busy || 'problem' in edit}>Registrar versión editada</button>
            <button type="button" disabled={busy} onClick={() => setMode('idle')}>Cancelar</button>
            {'problem' in edit && <span className="muted small">{edit.problem}</span>}
          </div>
        </form>
      )}

      {open && !suggestion.decision && mode === 'discard' && (
        <form className="form" style={{ marginTop: 12 }} onSubmit={submitDiscard} aria-label="Descartar sugerencia">
          <Field label="Motivo (opcional)" wide>
            <input value={reason} onChange={(e) => setReason(e.target.value)} />
          </Field>
          <div className="form-actions">
            <button className="danger" disabled={busy}>Descartar sugerencia</button>
            <button type="button" disabled={busy} onClick={() => setMode('idle')}>Cancelar</button>
          </div>
        </form>
      )}
      <ErrorLine message={error} />
    </article>
  )
}

function DecisionView({ decision }: { decision: SuggestionDecisionView }) {
  const { nameOf } = useParties()
  return (
    <div className="decision">
      <Badge tone={verdictTone[decision.verdict]}>{label(decision.verdict)}</Badge>{' '}
      <span className="muted small">por {nameOf(decision.by)} · {formatInstant(decision.at)}</span>
      {decision.verdict === 'EDITED' && (
        <p className="small" style={{ margin: '6px 0 0' }}>
          Registrada como <strong>{decision.criterionId}</strong> · {label(decision.result)} · severidad{' '}
          {label(decision.severity).toLowerCase()}: {decision.description}
          {decision.note && <span className="muted"> — {decision.note}</span>}
        </p>
      )}
      {decision.verdict === 'DISCARDED' && decision.reason && (
        <p className="small muted" style={{ margin: '6px 0 0' }}>Motivo: {decision.reason}</p>
      )}
    </div>
  )
}
