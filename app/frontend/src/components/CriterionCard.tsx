import { useState } from 'react'
import { api } from '../lib/api'
import { label } from '../lib/labels'
import { describeAnswer, parseNumber } from '../lib/rules'
import type { Answer, Criterion, CriterionLine } from '../lib/types'
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
