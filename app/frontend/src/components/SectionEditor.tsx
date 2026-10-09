import { useState, type FormEvent, type ReactNode } from 'react'
import { label } from '../lib/labels'
import {
  newBand, newCriterionForm, newEvidenceForm, newRuleForm, toSection, type BandForm, type CriterionForm,
  type EvidenceForm, type RuleForm, type SectionForm,
} from '../lib/ruleForm'
import { emptyOutcome } from '../lib/rules'
import type { Outcome, Rule, Section } from '../lib/types'
import { Card, Field, Notice } from './ui'

/** Builds one section of a schema draft: its criteria, how each is evaluated and what evidence it asks for. */
export function SectionEditor({ subsystems, nextOrder, onAdd, onCancel }: {
  subsystems: string[]
  nextOrder: number
  onAdd: (section: Section) => Promise<boolean>
  onCancel: () => void
}) {
  const [form, setForm] = useState<SectionForm>({ name: '', order: String(nextOrder), criteria: [newCriterionForm()] })
  const [problem, setProblem] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const update = (index: number, criterion: CriterionForm) =>
    setForm({ ...form, criteria: form.criteria.map((c, i) => (i === index ? criterion : c)) })

  async function submit(event: FormEvent) {
    event.preventDefault()
    let section: Section
    try {
      section = toSection(form)
    } catch (failure) {
      setProblem(failure instanceof Error ? failure.message : 'Revisá los datos.')
      return
    }
    setProblem(null)
    setBusy(true)
    const ok = await onAdd(section)
    setBusy(false)
    if (ok) onCancel()
  }

  return (
    <Card title="Nueva sección">
      <form className="stack" onSubmit={submit}>
        <div className="form">
          <Field label="Nombre de la sección">
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          </Field>
          <Field label="Orden" hint="Define en qué lugar aparece.">
            <input inputMode="numeric" value={form.order} onChange={(e) => setForm({ ...form, order: e.target.value })} required />
          </Field>
        </div>

        {form.criteria.map((criterion, index) => (
          <CriterionEditor
            key={index}
            index={index}
            criterion={criterion}
            subsystems={subsystems}
            onChange={(next) => update(index, next)}
            onRemove={form.criteria.length > 1 ? () => setForm({ ...form, criteria: form.criteria.filter((_, i) => i !== index) }) : undefined}
          />
        ))}
        <div>
          <button type="button" onClick={() => setForm({ ...form, criteria: [...form.criteria, newCriterionForm()] })}>
            Agregar criterio
          </button>
        </div>

        {problem && <Notice kind="error">{problem}</Notice>}
        <div className="row">
          <button className="primary" disabled={busy}>Agregar sección al borrador</button>
          <button type="button" onClick={onCancel}>Cancelar</button>
        </div>
      </form>
    </Card>
  )
}

function CriterionEditor({ index, criterion, subsystems, onChange, onRemove }: {
  index: number
  criterion: CriterionForm
  subsystems: string[]
  onChange: (criterion: CriterionForm) => void
  onRemove?: () => void
}) {
  const set = (patch: Partial<CriterionForm>) => onChange({ ...criterion, ...patch })
  const setRule = (patch: Partial<RuleForm>) => set({ rule: { ...criterion.rule, ...patch } })
  const rule = criterion.rule

  return (
    <fieldset className="criterion" style={{ margin: 0 }}>
      <legend><strong>Criterio {index + 1}</strong></legend>
      <div className="form">
        <Field label="Identificador" hint="Corto y único, por ejemplo TEMP.">
          <input value={criterion.id} onChange={(e) => set({ id: e.target.value })} required />
        </Field>
        <Field label="Cómo se responde">
          <select value={rule.type} onChange={(e) => setRule({ ...newRuleForm(e.target.value as Rule['type']), type: e.target.value as Rule['type'] })}>
            <option value="YES_NO">Sí o no</option>
            <option value="OPTIONS">Una opción de una lista</option>
            <option value="NUMERIC_RANGE">Una medición</option>
          </select>
        </Field>
        {subsystems.length > 0 && (
          <Field label="Subsistema" hint="Si no elegís, pesa sobre todos.">
            <select value={criterion.subsystem} onChange={(e) => set({ subsystem: e.target.value })}>
              <option value="">Todos</option>
              {subsystems.map((s) => <option key={s} value={s}>{label(s)}</option>)}
            </select>
          </Field>
        )}
      </div>

      {rule.type === 'YES_NO' && (
        <div className="stack" style={{ marginTop: 12 }}>
          <OutcomeFields title="Si responde «Sí»" outcome={rule.yes} onChange={(yes) => setRule({ yes })} />
          <OutcomeFields title="Si responde «No»" outcome={rule.no} onChange={(no) => setRule({ no })} />
        </div>
      )}
      {rule.type === 'OPTIONS' && <OptionsEditor rule={rule} onChange={setRule} />}
      {rule.type === 'NUMERIC_RANGE' && <NumericEditor rule={rule} onChange={setRule} />}

      <EvidenceEditor evidence={criterion.evidence} onChange={(evidence) => set({ evidence })} />
      {onRemove && <div style={{ marginTop: 12 }}><button type="button" className="small danger" onClick={onRemove}>Quitar criterio</button></div>}
    </fieldset>
  )
}

function OutcomeFields({ title, outcome, onChange }: { title: ReactNode; outcome: Outcome; onChange: (outcome: Outcome) => void }) {
  const set = (patch: Partial<Outcome>) => onChange({ ...outcome, ...patch })
  return (
    <div>
      <div className="section-title">{title}</div>
      <div className="outcome">
        <Field label="Código"><input value={outcome.code} onChange={(e) => set({ code: e.target.value })} required /></Field>
        <Field label="Resultado">
          <select value={outcome.result} onChange={(e) => {
            const result = e.target.value as Outcome['result']
            set({ result, severity: result === 'APPROVED' ? null : (outcome.severity ?? 'MEDIUM') })
          }}>
            <option value="APPROVED">Aprobado</option>
            <option value="OBSERVED">Observado</option>
            <option value="REJECTED">Rechazado</option>
          </select>
        </Field>
        <Field label="Severidad">
          <select value={outcome.severity ?? ''} disabled={outcome.result === 'APPROVED'} onChange={(e) => set({ severity: (e.target.value || null) as Outcome['severity'] })}>
            {outcome.result === 'APPROVED' && <option value="">—</option>}
            {(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const).map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </select>
        </Field>
        <Field label="Descripción"><input value={outcome.description} onChange={(e) => set({ description: e.target.value })} required /></Field>
      </div>
    </div>
  )
}

function OptionsEditor({ rule, onChange }: { rule: RuleForm; onChange: (patch: Partial<RuleForm>) => void }) {
  return (
    <div style={{ marginTop: 12 }}>
      {rule.options.map((option, i) => (
        <div className="option-row" key={i}>
          <Field label={`Opción ${i + 1}`}>
            <input value={option.key} onChange={(e) => onChange({ options: rule.options.map((o, j) => (j === i ? { ...o, key: e.target.value } : o)) })} required />
          </Field>
          <OutcomeFields title="Qué significa" outcome={option.outcome} onChange={(outcome) => onChange({ options: rule.options.map((o, j) => (j === i ? { ...o, outcome } : o)) })} />
          {rule.options.length > 2 && (
            <div><button type="button" className="small danger" onClick={() => onChange({ options: rule.options.filter((_, j) => j !== i) })}>Quitar opción</button></div>
          )}
        </div>
      ))}
      <button type="button" onClick={() => onChange({ options: [...rule.options, { key: '', outcome: emptyOutcome('APPROVED') }] })}>Agregar opción</button>
    </div>
  )
}

function NumericEditor({ rule, onChange }: { rule: RuleForm; onChange: (patch: Partial<RuleForm>) => void }) {
  const setBand = (i: number, patch: Partial<BandForm>) => onChange({ bands: rule.bands.map((b, j) => (j === i ? { ...b, ...patch } : b)) })
  return (
    <div style={{ marginTop: 12 }}>
      <div className="form">
        <Field label="Unidad"><input value={rule.unit} onChange={(e) => onChange({ unit: e.target.value })} required /></Field>
        <Field label="Mínimo posible"><input inputMode="decimal" value={rule.minimum} onChange={(e) => onChange({ minimum: e.target.value })} required /></Field>
        <Field label="Máximo posible"><input inputMode="decimal" value={rule.maximum} onChange={(e) => onChange({ maximum: e.target.value })} required /></Field>
      </div>
      <p className="muted small">Los tramos tienen que cubrir todo el rango, del mínimo al máximo, sin superponerse.</p>
      {rule.bands.map((band, i) => (
        <div className="option-row" key={i}>
          <div className="form">
            <Field label="Desde"><input inputMode="decimal" value={band.lower} onChange={(e) => setBand(i, { lower: e.target.value })} /></Field>
            <label className="checks"><input type="checkbox" checked={band.lowerInclusive} onChange={(e) => setBand(i, { lowerInclusive: e.target.checked })} /> Incluye el valor inicial</label>
            <Field label="Hasta"><input inputMode="decimal" value={band.upper} onChange={(e) => setBand(i, { upper: e.target.value })} /></Field>
            <label className="checks"><input type="checkbox" checked={band.upperInclusive} onChange={(e) => setBand(i, { upperInclusive: e.target.checked })} /> Incluye el valor final</label>
          </div>
          <OutcomeFields title={`Tramo ${i + 1}`} outcome={band.outcome} onChange={(outcome) => setBand(i, { outcome })} />
          {rule.bands.length > 1 && <div><button type="button" className="small danger" onClick={() => onChange({ bands: rule.bands.filter((_, j) => j !== i) })}>Quitar tramo</button></div>}
        </div>
      ))}
      <button type="button" onClick={() => onChange({ bands: [...rule.bands, newBand()] })}>Agregar tramo</button>
    </div>
  )
}

function EvidenceEditor({ evidence, onChange }: { evidence: EvidenceForm[]; onChange: (evidence: EvidenceForm[]) => void }) {
  const set = (i: number, patch: Partial<EvidenceForm>) => onChange(evidence.map((e, j) => (j === i ? { ...e, ...patch } : e)))
  return (
    <div style={{ marginTop: 14 }}>
      <div className="section-title">Evidencia que se pide (opcional)</div>
      {evidence.map((item, i) => (
        <div className="option-row" key={i}>
          <div className="form">
            <Field label="Qué se pide"><input value={item.label} onChange={(e) => set(i, { label: e.target.value })} required /></Field>
            <Field label="Tipo">
              <select value={item.type} onChange={(e) => set(i, { type: e.target.value as EvidenceForm['type'] })}>
                <option value="DOCUMENT">Documento</option>
                <option value="PHOTOGRAPH">Fotografía</option>
              </select>
            </Field>
            <Field label="Cantidad mínima"><input inputMode="numeric" value={item.minimumCount} onChange={(e) => set(i, { minimumCount: e.target.value })} /></Field>
            <label className="checks"><input type="checkbox" checked={item.mandatory} onChange={(e) => set(i, { mandatory: e.target.checked })} /> Obligatoria</label>
          </div>
          <div><button type="button" className="small danger" onClick={() => onChange(evidence.filter((_, j) => j !== i))}>Quitar</button></div>
        </div>
      ))}
      <button type="button" className="small" onClick={() => onChange([...evidence, newEvidenceForm()])}>Pedir evidencia</button>
    </div>
  )
}

