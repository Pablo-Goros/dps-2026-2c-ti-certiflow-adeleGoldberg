import type { Answer, Band, Criterion, Outcome, Rule, SchemaVersion } from './types'

/** Every criterion of a version, by id, so an inspection can show the right input for each one. */
export function criteriaById(version: SchemaVersion | undefined): Map<string, Criterion> {
  const found = new Map<string, Criterion>()
  version?.sections.forEach((section) => section.criteria.forEach((criterion) => found.set(criterion.id, criterion)))
  return found
}

/** Text of an answer for reading. */
export function describeAnswer(answer: Answer | null): string {
  if (!answer) return 'Sin responder'
  switch (answer.type) {
    case 'YES_NO':
      return answer.affirmative ? 'Sí' : 'No'
    case 'OPTION':
      return answer.option ?? '—'
    case 'MEASUREMENT':
      return `${answer.value ?? ''} ${answer.unit ?? ''}`.trim()
  }
}

/** The interval of a band as people write it: "[2, 8]" or "(8, 150]". */
export function describeBand(band: Band): string {
  const lower = band.lower === null ? '−∞' : String(band.lower)
  const upper = band.upper === null ? '∞' : String(band.upper)
  return `${band.lowerInclusive ? '[' : '('}${lower}, ${upper}${band.upperInclusive ? ']' : ')'}`
}

export function describeRule(rule: Rule): string {
  switch (rule.type) {
    case 'YES_NO':
      return 'Sí / No'
    case 'OPTIONS':
      return 'Opciones: ' + Object.keys(rule.options ?? {}).join(', ')
    case 'NUMERIC_RANGE':
      return `Medición en ${rule.unit ?? ''} (${rule.minimum} a ${rule.maximum})`
  }
}

export function emptyOutcome(result: Outcome['result'] = 'APPROVED'): Outcome {
  return { code: '', result, severity: null, description: '' }
}

/** Turns what the form holds into the outcome the API expects (a severity only makes sense when not approved). */
export function cleanOutcome(outcome: Outcome): Outcome {
  return {
    code: outcome.code.trim(),
    result: outcome.result,
    severity: outcome.result === 'APPROVED' ? null : outcome.severity,
    description: outcome.description.trim(),
  }
}

/** Parses a number typed in a form, accepting a decimal comma. Empty or invalid text gives null. */
export function parseNumber(text: string): number | null {
  const normalized = text.trim().replace(',', '.')
  if (normalized === '') return null
  const value = Number(normalized)
  return Number.isFinite(value) ? value : null
}
