import { afterEach, describe, expect, it } from 'vitest'
import { ApiError, describeError } from './api'
import { humanizeDomainMessage, setNameResolver } from './errors'

const ANA = 'cf8d6e74-7fa3-4f3a-a3fd-8195bc68c103'
const JUAN = '52543e3c-6301-46f6-b69f-b43bb17ab8d3'
const INSPECTION = '6c4abc8c-2787-4898-b5b9-2e10f46f1b87'
const names: Record<string, string> = { [ANA]: 'Ana Pérez', [JUAN]: 'Juan Gómez' }

afterEach(() => setNameResolver(null))

describe('refusals of the core as the person reads them', () => {
  it.each([
    ['record an answer', 'registrar una respuesta'],
    ['close', 'cerrarla'],
    ['consult the assistant about', 'consultar al asistente'],
    ['record an observed non-conformity', 'registrar una no conformidad observada'],
  ])('explains who may %s, in Spanish and by name', (operation, spanish) => {
    setNameResolver((id) => names[id] ?? id)

    const text = humanizeDomainMessage(
      `only the assigned inspector ${ANA} may ${operation} inspection ${INSPECTION}, not ${JUAN}`)

    expect(text).toBe(`Solo Ana Pérez, que tiene asignada esta inspección, puede ${spanish}. `
      + 'Estás actuando como Juan Gómez: elegí a Ana Pérez en «Actuando como».')
  })

  it('replaces the ids of known people in any other message and leaves the rest as it is', () => {
    setNameResolver((id) => names[id] ?? id)

    expect(humanizeDomainMessage(`party ${ANA} is not a person; inspection ${INSPECTION} is closed`))
      .toBe(`party Ana Pérez is not a person; inspection ${INSPECTION} is closed`)
  })

  it('keeps the ids while no directory has registered names', () => {
    expect(humanizeDomainMessage(`only the assigned inspector ${ANA} may start inspection ${INSPECTION}, not ${JUAN}`))
      .toBe(`Solo ${ANA}, que tiene asignada esta inspección, puede iniciarla. Estás actuando como ${JUAN}: elegí a ${ANA} en «Actuando como».`)
  })

  it('is what describeError shows for a refused request', () => {
    setNameResolver((id) => names[id] ?? id)
    const refused = new ApiError(422, 'BUSINESS_RULE',
      `only the assigned inspector ${ANA} may close inspection ${INSPECTION}, not ${JUAN}`)

    expect(describeError(refused)).toContain('Solo Ana Pérez')
  })
})
