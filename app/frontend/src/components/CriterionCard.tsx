import { useState, type FormEvent } from 'react'
import { api } from '../lib/api'
import { originLabel } from '../lib/assistant'
import { label } from '../lib/labels'
import { describeAnswer, parseNumber } from '../lib/rules'
import type { Answer, Criterion, CriterionLine, CriterionResult, Severity } from '../lib/types'
import { Badge, ErrorLine, ResultBadge, SeverityBadge, useAction } from './ui'

interface Props {
  inspectionId: string
  line: CriterionLine
  criterion: Criterion | undefined
  editable: boolean
  onChange: () => void
}

/** One criterion of an inspection: its question, the answer input, the evidence and the verdict. */
export function CriterionCard({ inspectionId, line, criterion, editable, onChange }: Props) {
  const { run, busy, error } = useAction()
  const tone = line.evaluation ? line.evaluation.result.toLowerCase() : ''

  const save = (answer: Answer) =>
    run(async () => {
      await api.put(`/inspections/${inspectionId}/answers/${encodeURIComponent(line.criterionId)}`, answer)
      onChange()
    })
  const clear = () =>
    run(async () => {
      await api.del(`/inspections/${inspectionId}/answers/${encodeURIComponent(line.criterionId)}`)
      onChange()
    })

  if (!line.applicable) {
    return (
      <div className="criterion">
        <strong>{line.criterionId}</strong> <span className="muted">No aplica a este activo.</span>
      </div>
    )
  }

  return (
    <div className={`criterion ${tone}`}>
      <header>
        <div>
          <strong>{line.criterionId}</strong>{' '}
          {line.subsystem && <Badge tone="seal">{label(line.subsystem)}</Badge>}
        </div>
        {line.evaluation && (
          <div className="row">
            <ResultBadge result={line.evaluation.result} />
            <SeverityBadge severity={line.evaluation.severity} />
          </div>
        )}
      </header>

      {editable && criterion ? (
        <div className="answer">
          <AnswerInput criterion={criterion} current={line.answer} busy={busy} onSave={save} />
          {line.answer && (
            <button className="small" disabled={busy} onClick={clear}>Quitar respuesta</button>
          )}
        </div>
      ) : (
        <p style={{ margin: 0 }}>
          <span className="muted">Respuesta: </span>
          <strong>{describeAnswer(line.answer)}</strong>
        </p>
      )}

      {line.evaluation && line.evaluation.reasons.length > 0 && (
        <ul className="reasons">
          {line.evaluation.reasons.map((reason, i) => <li key={i}>{reason}</li>)}
        </ul>
      )}

      {criterion && criterion.evidence.length > 0 && (
        <EvidenceBlock inspectionId={inspectionId} line={line} criterion={criterion} editable={editable} onChange={onChange} />
      )}
      <ObservationsBlock inspectionId={inspectionId} line={line} editable={editable} onChange={onChange} />
      <ErrorLine message={error} />
    </div>
  )
}

const NON_CONFORMING: CriterionResult[] = ['OBSERVED', 'REJECTED']
const SEVERITIES: Severity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

/**
 * Non-conformities seen beyond what the rule measures, each with who proposed it: the assistant
 * (accepted or edited by the inspector) or the inspector alone.
 */
function ObservationsBlock({ inspectionId, line, editable, onChange }: {
  inspectionId: string
  line: CriterionLine
  editable: boolean
  onChange: () => void
}) {
  const { run, busy, error } = useAction()
  const [adding, setAdding] = useState(false)
  const [result, setResult] = useState<CriterionResult>('OBSERVED')
  const [severity, setSeverity] = useState<Severity>('MEDIUM')
  const [description, setDescription] = useState('')
  const observations = line.observedNonConformities ?? []
  const base = `/inspections/${inspectionId}/criteria/${encodeURIComponent(line.criterionId)}/observed-nonconformities`

  const record = (event: FormEvent) => {
    event.preventDefault()
    run(async () => {
      await api.post(base, { result, severity, description: description.trim() })
      setDescription('')
      setAdding(false)
      onChange()
    }, 'No conformidad registrada')
  }
  const withdraw = (observationId: string) =>
    run(async () => {
      await api.del(`${base}/${encodeURIComponent(observationId)}`)
      onChange()
    }, 'No conformidad retirada')

  if (observations.length === 0 && !editable) return null
  return (
    <div style={{ marginTop: 12 }}>
      <div className="section-title">No conformidades observadas</div>
      {observations.length > 0 && (
        <ul className="evidence-list">
          {observations.map((observation) => (
            <li key={observation.id} className="observation">
              <ResultBadge result={observation.result} />
              <SeverityBadge severity={observation.severity} />
              <span>{observation.description}</span>
              <Badge tone={observation.origin ? 'info' : 'neutral'} icon={observation.origin ? 'bolt' : 'user'}>
                {originLabel(observation.origin)}
              </Badge>
              {editable && (
                <button className="small danger" disabled={busy} onClick={() => withdraw(observation.id)}>Quitar</button>
              )}
            </li>
          ))}
        </ul>
      )}
      {editable && !adding && (
        <button className="small" style={{ marginTop: 8 }} disabled={busy} onClick={() => setAdding(true)}>
          Registrar no conformidad
        </button>
      )}
      {editable && adding && (
        <form className="row" style={{ marginTop: 8 }} onSubmit={record} aria-label={`Registrar no conformidad en ${line.criterionId}`}>
          <select style={{ width: 'auto' }} aria-label="Resultado" value={result} onChange={(e) => setResult(e.target.value as CriterionResult)}>
            {NON_CONFORMING.map((value) => <option key={value} value={value}>{label(value)}</option>)}
          </select>
          <select style={{ width: 'auto' }} aria-label="Severidad" value={severity} onChange={(e) => setSeverity(e.target.value as Severity)}>
            {SEVERITIES.map((value) => <option key={value} value={value}>{label(value)}</option>)}
          </select>
          <input style={{ flex: 1, minWidth: 180 }} aria-label="Descripción" placeholder="Qué observaste" value={description} onChange={(e) => setDescription(e.target.value)} />
          <button disabled={busy || !description.trim()}>Registrar</button>
          <button type="button" disabled={busy} onClick={() => setAdding(false)}>Cancelar</button>
        </form>
      )}
      <ErrorLine message={error} />
    </div>
  )
}

function AnswerInput({ criterion, current, busy, onSave }: {
  criterion: Criterion
  current: Answer | null
  busy: boolean
  onSave: (answer: Answer) => void
}) {
  const rule = criterion.rule
  const [text, setText] = useState(current?.type === 'MEASUREMENT' && current.value !== null ? String(current.value) : '')

  if (rule.type === 'YES_NO') {
    return (
      <div className="segmented" role="group" aria-label="Respuesta">
        {[true, false].map((value) => (
          <button
            key={String(value)}
            type="button"
            disabled={busy}
            aria-pressed={current?.type === 'YES_NO' && current.affirmative === value}
            onClick={() => onSave({ type: 'YES_NO', affirmative: value })}
          >
            {value ? 'Sí' : 'No'}
          </button>
        ))}
      </div>
    )
  }
  if (rule.type === 'OPTIONS') {
    return (
      <select
        aria-label="Respuesta"
        disabled={busy}
        value={current?.type === 'OPTION' ? (current.option ?? '') : ''}
        onChange={(e) => e.target.value && onSave({ type: 'OPTION', option: e.target.value })}
      >
        <option value="">Elegí una opción…</option>
        {Object.keys(rule.options ?? {}).map((key) => <option key={key} value={key}>{key}</option>)}
      </select>
    )
  }
  const value = parseNumber(text)
  return (
    <form
      className="answer"
      onSubmit={(e) => {
        e.preventDefault()
        if (value !== null) onSave({ type: 'MEASUREMENT', value, unit: rule.unit ?? '' })
      }}
    >
      <input
        inputMode="decimal"
        aria-label="Medición"
        placeholder={`${rule.minimum} a ${rule.maximum}`}
        value={text}
        onChange={(e) => setText(e.target.value)}
      />
      <span className="muted">{rule.unit}</span>
      <button disabled={busy || value === null}>Guardar medición</button>
    </form>
  )
}

function EvidenceBlock({ inspectionId, line, criterion, editable, onChange }: {
  inspectionId: string
  line: CriterionLine
  criterion: Criterion
  editable: boolean
  onChange: () => void
}) {
  const { run, busy, error } = useAction()
  const [requirement, setRequirement] = useState(criterion.evidence[0].label)
  const [reference, setReference] = useState('')

  const attach = () =>
    run(async () => {
      await api.post(`/inspections/${inspectionId}/evidence`, {
        criterionId: line.criterionId,
        requirementLabel: requirement,
        reference: reference.trim(),
      })
      setReference('')
      onChange()
    }, 'Evidencia adjuntada')

  const remove = (evidenceId: string) =>
    run(async () => {
      await api.del(`/inspections/${inspectionId}/criteria/${encodeURIComponent(line.criterionId)}/evidence/${encodeURIComponent(evidenceId)}`)
      onChange()
    })

  return (
    <div style={{ marginTop: 12 }}>
      <div className="section-title">Evidencia requerida</div>
      <ul className="evidence-list">
        {criterion.evidence.map((req) => {
          const attached = line.evidence.filter((e) => e.requirementLabel === req.label)
          return (
            <li key={req.label}>
              <Badge tone={attached.length >= req.minimumCount ? 'ok' : req.mandatory ? 'warn' : 'neutral'}>
                {attached.length}/{req.minimumCount}
              </Badge>
              <span>{req.label} · {label(req.type)}{req.mandatory ? '' : ' (opcional)'}</span>
            </li>
          )
        })}
      </ul>
      {line.evidence.length > 0 && (
        <ul className="evidence-list" style={{ marginTop: 8 }}>
          {line.evidence.map((e) => (
            <li key={e.id}>
              <span className="mono">{e.reference}</span>
              <span className="muted small">({e.requirementLabel})</span>
              {editable && <button className="small danger" disabled={busy} onClick={() => remove(e.id)}>Quitar</button>}
            </li>
          ))}
        </ul>
      )}
      {editable && (
        <form className="row" style={{ marginTop: 8 }} onSubmit={(e) => { e.preventDefault(); attach() }}>
          <select style={{ width: 'auto' }} aria-label="Requisito" value={requirement} onChange={(e) => setRequirement(e.target.value)}>
            {criterion.evidence.map((req) => <option key={req.label}>{req.label}</option>)}
          </select>
          <input style={{ flex: 1, minWidth: 180 }} aria-label="Referencia" placeholder="Nombre del archivo o enlace" value={reference} onChange={(e) => setReference(e.target.value)} />
          <button disabled={busy || !reference.trim()}>Adjuntar</button>
        </form>
      )}
      <ErrorLine message={error} />
    </div>
  )
}
