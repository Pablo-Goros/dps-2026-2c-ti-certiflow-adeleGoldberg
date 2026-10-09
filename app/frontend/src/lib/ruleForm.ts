import { cleanOutcome, emptyOutcome, parseNumber } from './rules'
import type { Band, Criterion, EvidenceRequirement, Outcome, Rule, Section } from './types'

// Form state of the schema editor, and the conversion to what the API expects. The forms keep
// numbers as text so a half-typed value is never lost; conversion is where it becomes a number.

export interface BandForm {
  lower: string
  lowerInclusive: boolean
  upper: string
  upperInclusive: boolean
  outcome: Outcome
}

export interface RuleForm {
  type: Rule['type']
  yes: Outcome
  no: Outcome
  options: { key: string; outcome: Outcome }[]
  unit: string
  minimum: string
  maximum: string
  bands: BandForm[]
}

export interface EvidenceForm {
  type: EvidenceRequirement['type']
  label: string
  mandatory: boolean
  minimumCount: string
}

export interface CriterionForm {
  id: string
  subsystem: string
  rule: RuleForm
  evidence: EvidenceForm[]
}

export interface SectionForm {
  name: string
  order: string
  criteria: CriterionForm[]
}

export function newRuleForm(type: Rule['type'] = 'YES_NO'): RuleForm {
  return {
    type,
    yes: { code: 'OK', result: 'APPROVED', severity: null, description: 'Cumple' },
    no: { code: 'NOT_OK', result: 'OBSERVED', severity: 'MEDIUM', description: 'No cumple' },
    options: [
      { key: '', outcome: emptyOutcome('APPROVED') },
      { key: '', outcome: { code: '', result: 'OBSERVED', severity: 'MEDIUM', description: '' } },
    ],
    unit: '',
    minimum: '',
    maximum: '',
    bands: [newBand()],
  }
}

export function newBand(): BandForm {
  return { lower: '', lowerInclusive: true, upper: '', upperInclusive: false, outcome: emptyOutcome('APPROVED') }
}

export function newCriterionForm(): CriterionForm {
  return { id: '', subsystem: '', rule: newRuleForm(), evidence: [] }
}

export function newEvidenceForm(): EvidenceForm {
  return { type: 'DOCUMENT', label: '', mandatory: true, minimumCount: '1' }
}

function required(value: number | null, name: string): number {
  if (value === null) throw new Error(`Falta ${name} o no es un número.`)
  return value
}

function toBand(band: BandForm, index: number): Band {
  return {
    lower: parseNumber(band.lower),
    lowerInclusive: band.lowerInclusive,
    upper: parseNumber(band.upper),
    upperInclusive: band.upperInclusive,
    outcome: cleanOutcome({ ...band.outcome, description: band.outcome.description || `Tramo ${index + 1}` }),
  }
}

export function toRule(form: RuleForm): Rule {
  switch (form.type) {
    case 'YES_NO':
      return { type: 'YES_NO', yes: cleanOutcome(form.yes), no: cleanOutcome(form.no) }
    case 'OPTIONS': {
      const options: Record<string, Outcome> = {}
      for (const option of form.options) {
        const key = option.key.trim()
        if (!key) throw new Error('Cada opción necesita un nombre.')
        if (key in options) throw new Error(`La opción «${key}» está repetida.`)
        options[key] = cleanOutcome(option.outcome)
      }
      if (Object.keys(options).length < 2) throw new Error('Una regla de opciones necesita al menos dos opciones.')
      return { type: 'OPTIONS', options }
    }
    case 'NUMERIC_RANGE':
      if (form.bands.length === 0) throw new Error('Agregá al menos un tramo.')
      return {
        type: 'NUMERIC_RANGE',
        unit: form.unit.trim(),
        minimum: required(parseNumber(form.minimum), 'el mínimo'),
        maximum: required(parseNumber(form.maximum), 'el máximo'),
        bands: form.bands.map(toBand),
      }
  }
}

export function toSection(form: SectionForm): Section {
  const name = form.name.trim()
  if (!name) throw new Error('La sección necesita un nombre.')
  if (form.criteria.length === 0) throw new Error('La sección necesita al menos un criterio.')
  const seen = new Set<string>()
  return {
    name,
    order: required(parseNumber(form.order), 'el orden'),
    criteria: form.criteria.map((criterion) => {
      const id = criterion.id.trim()
      if (!id) throw new Error('Cada criterio necesita un identificador.')
      if (seen.has(id)) throw new Error(`El criterio «${id}» está repetido.`)
      seen.add(id)
      return {
        id,
        subsystem: criterion.subsystem || null,
        rule: toRule(criterion.rule),
        evidence: criterion.evidence.map((e) => ({
          type: e.type,
          label: e.label.trim(),
          mandatory: e.mandatory,
          minimumCount: Math.max(1, Math.trunc(parseNumber(e.minimumCount) ?? 1)),
        })),
      } satisfies Criterion
    }),
  }
}
