import { useState, type FormEvent } from 'react'
import { api } from '../lib/api'
import { parseNumber } from '../lib/rules'
import type { Answer, Criterion, Inspection, SchemaVersion } from '../lib/types'
import { Card, ErrorLine, Field, useAction } from './ui'

type Correction =
  | { type: 'ANSWER'; criterionId: string; answer: Answer }
  | { type: 'EVIDENCE_REFERENCE'; criterionId: string; evidenceId: string; reference: string }
  | { type: 'NOTE'; noteId: string; text: string }

export function RectificationForm({ inspection, version, onChange }: {
  inspection: Inspection
  version: SchemaVersion | undefined
  onChange: () => void
}) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [answerChanges, setAnswerChanges] = useState<Record<string, Answer>>({})
  const [evidenceChanges, setEvidenceChanges] = useState<Record<string, string>>({})
  const [noteChanges, setNoteChanges] = useState<Record<string, string>>({})
  const { run, busy, error } = useAction()

  const corrections: Correction[] = [
    ...Object.entries(answerChanges).map(([criterionId, answer]) => ({ type: 'ANSWER' as const, criterionId, answer })),
    ...Object.entries(evidenceChanges).map(([key, reference]) => {
      const [criterionId, evidenceId] = key.split('\u0000')
      return { type: 'EVIDENCE_REFERENCE' as const, criterionId, evidenceId, reference }
    }),
    ...Object.entries(noteChanges).map(([noteId, text]) => ({ type: 'NOTE' as const, noteId, text })),
  ]

  function setAnswer(criterionId: string, original: Answer | null, answer: Answer | null) {
    setAnswerChanges((current) => {
      const next = { ...current }
      if (!answer || sameAnswer(answer, original)) delete next[criterionId]
      else next[criterionId] = answer
      return next
    })
  }

  function setEvidenceReference(criterionId: string, evidenceId: string, original: string, reference: string) {
    setEvidenceChanges((current) => {
      const next = { ...current }
      const key = `${criterionId}\u0000${evidenceId}`
      if (reference === original) delete next[key]
      else next[key] = reference
      return next
    })
  }

  function setNoteText(noteId: string, original: string, text: string) {
    setNoteChanges((current) => {
      const next = { ...current }
      if (text === original) delete next[noteId]
      else next[noteId] = text
      return next
    })
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    const saved = await run(async () => {
      await api.post(`/inspections/${inspection.id}/rectifications`, { reason: reason.trim(), corrections })
      onChange()
      setOpen(false)
      setReason('')
      setAnswerChanges({})
      setEvidenceChanges({})
      setNoteChanges({})
    }, 'Rectificación guardada en el acta')
    if (!saved) return
  }

  if (!open) {
    return <button className="primary" onClick={() => setOpen(true)}>Rectificar inspección</button>
  }

  return (
    <Card title="Rectificar inspección">
      <p className="muted">La corrección conserva el valor original, quién la hizo, cuándo y el motivo en el acta.</p>
      <form className="form" onSubmit={submit}>
        {inspection.criteria.filter((line) => line.applicable).map((line) => {
          const criterion = version?.sections.flatMap((section) => section.criteria).find((item) => item.id === line.criterionId)
          return (
            <fieldset className="field field-wide" key={line.criterionId}>
              <legend>{line.criterionId}</legend>
              {criterion && (
                <AnswerCorrectionField
                  criterion={criterion}
                  current={line.answer}
                  onChange={(answer) => setAnswer(line.criterionId, line.answer, answer)}
                />
              )}
              {line.evidence.map((evidence) => (
                <Field key={evidence.id} label={`Nueva referencia de ${line.criterionId} · ${evidence.requirementLabel}`}>
                  <input
                    value={evidenceChanges[`${line.criterionId}\u0000${evidence.id}`] ?? evidence.reference}
                    onChange={(event) => setEvidenceReference(line.criterionId, evidence.id, evidence.reference, event.target.value)}
                  />
                </Field>
              ))}
            </fieldset>
          )
        })}
        {inspection.notes.map((note) => (
          <Field key={note.id} label="Corregir nota">
            <textarea value={noteChanges[note.id] ?? note.text} onChange={(event) => setNoteText(note.id, note.text, event.target.value)} />
          </Field>
        ))}
        <Field label="Motivo de rectificación" wide>
          <textarea required value={reason} onChange={(event) => setReason(event.target.value)} />
        </Field>
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={() => setOpen(false)}>Cancelar</button>
          <button className="primary" disabled={busy || !reason.trim() || corrections.length === 0}>
            Guardar rectificación
          </button>
        </div>
        <ErrorLine message={error} />
      </form>
    </Card>
  )
}

function AnswerCorrectionField({ criterion, current, onChange }: {
  criterion: Criterion
  current: Answer | null
  onChange: (answer: Answer | null) => void
}) {
  const rule = criterion.rule
  if (rule.type === 'YES_NO') {
    const selected = current?.type === 'YES_NO' ? String(current.affirmative) : ''
    return (
      <Field label={`Respuesta corregida de ${criterion.id}`}>
        <select aria-label={`Respuesta corregida de ${criterion.id}`} value={selected} onChange={(event) => {
          if (!event.target.value) onChange(null)
          else onChange({ type: 'YES_NO', affirmative: event.target.value === 'true' })
        }}>
          <option value="">Sin cambios</option><option value="true">Sí</option><option value="false">No</option>
        </select>
      </Field>
    )
  }
  if (rule.type === 'OPTIONS') {
    const selected = current?.type === 'OPTION' ? current.option ?? '' : ''
    return (
      <Field label={`Respuesta corregida de ${criterion.id}`}>
        <select aria-label={`Respuesta corregida de ${criterion.id}`} value={selected} onChange={(event) => {
          if (!event.target.value) onChange(null)
          else onChange({ type: 'OPTION', option: event.target.value })
        }}>
          <option value="">Sin cambios</option>
          {Object.keys(rule.options ?? {}).map((option) => <option key={option} value={option}>{option}</option>)}
        </select>
      </Field>
    )
  }
  const initial = current?.type === 'MEASUREMENT' && current.value !== null ? String(current.value) : ''
  return <MeasurementCorrection criterion={criterion} initial={initial} current={current} onChange={onChange} />
}

function MeasurementCorrection({ criterion, initial, current, onChange }: {
  criterion: Criterion
  initial: string
  current: Answer | null
  onChange: (answer: Answer | null) => void
}) {
  const [text, setText] = useState(initial)
  return (
    <Field label={`Nueva medición de ${criterion.id}`}>
      <div className="row">
        <input inputMode="decimal" aria-label={`Nueva medición de ${criterion.id}`} value={text} onChange={(event) => {
          const updated = event.target.value
          setText(updated)
          const parsed = parseNumber(updated)
          if (parsed === null) onChange(null)
          else {
            const answer: Answer = { type: 'MEASUREMENT', value: parsed, unit: criterion.rule.unit ?? '' }
            onChange(sameAnswer(answer, current) ? null : answer)
          }
        }} />
        <span className="muted">{criterion.rule.unit}</span>
      </div>
      <span className="field-hint">Dejá el valor original para no rectificar esta medición.</span>
    </Field>
  )
}

function sameAnswer(left: Answer, right: Answer | null): boolean {
  return !!right && JSON.stringify(left) === JSON.stringify(right)
}
