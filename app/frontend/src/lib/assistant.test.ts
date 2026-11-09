import { describe, expect, it } from 'vitest'
import { ApiError } from './api'
import { editFormOf, editRequest, originLabel, unavailableMessage, type EditForm } from './assistant'
import type { Suggestion } from './types'

const suggestion: Suggestion = {
  id: 's1', criterionId: 'ELEC', section: 'Eléctrica', result: 'REJECTED', severity: 'CRITICAL',
  description: 'Conductor expuesto junto al tablero', rationale: 'La nota describe un cable pelado', withoutNotes: null, decision: null,
}

describe('origin of an observed non-conformity', () => {
  it.each([
    [null, 'Del inspector'],
    [{ consultationId: 'c-1', suggestionId: 's1', edited: false, assistant: 'test m v1' }, 'Sugerida por IA'],
    [{ consultationId: 'c-1', suggestionId: 's1', edited: true, assistant: 'test m v1' }, 'Sugerida por IA, editada'],
  ])('labels %j as %s', (origin, expected) => {
    expect(originLabel(origin)).toBe(expected)
  })
})

describe('editing a suggestion', () => {
  const valid: EditForm = { ...editFormOf(suggestion), result: 'OBSERVED', severity: 'MEDIUM', note: ' gastada, no cortada ' }

  it('starts from what the assistant suggested, with no note', () => {
    expect(editFormOf(suggestion)).toEqual({
      criterionId: 'ELEC', result: 'REJECTED', severity: 'CRITICAL', description: 'Conductor expuesto junto al tablero', note: '',
    })
  })

  it('builds the EDITED body with trimmed text', () => {
    expect(editRequest(valid)).toEqual({
      request: {
        verdict: 'EDITED', criterionId: 'ELEC', result: 'OBSERVED', severity: 'MEDIUM',
        description: 'Conductor expuesto junto al tablero', note: 'gastada, no cortada',
      },
    })
  })

  it.each([
    ['an approving result', { result: 'APPROVED' as const }, 'descartá'],
    ['no criterion', { criterionId: '' }, 'criterio'],
    ['an empty description', { description: '  ' }, 'descripción'],
    ['an empty note', { note: ' ' }, 'por qué'],
  ])('refuses %s', (_, change, problem) => {
    const outcome = editRequest({ ...valid, ...change })
    expect('problem' in outcome && outcome.problem).toContain(problem)
  })
})

describe('an unavailable assistant', () => {
  const failure = (details: string[]) => new ApiError(503, 'ASSISTANT_UNAVAILABLE', 'assistant unavailable', details)

  it.each([
    ['UNREACHABLE', 'no respondió a tiempo'],
    ['REFUSED', 'se negó'],
    ['INCOMPLETE', 'largo'],
    ['MALFORMED', 'no se pudo interpretar'],
    ['MISCONFIGURED', 'configurado'],
    ['SOMETHING_NEW', 'no está disponible'],
  ])('explains %s and that nothing was recorded', (detail, words) => {
    const message = unavailableMessage(failure([detail]))
    expect(message).toContain(words)
    expect(message).toContain('No se registró nada.')
  })

  it('leaves other failures to the usual error line', () => {
    expect(unavailableMessage(new ApiError(422, 'BUSINESS_RULE', 'no notes'))).toBeNull()
    expect(unavailableMessage(new Error('boom'))).toBeNull()
  })
})
