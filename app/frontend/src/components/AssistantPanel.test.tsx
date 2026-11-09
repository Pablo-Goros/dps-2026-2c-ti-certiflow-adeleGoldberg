// @vitest-environment jsdom
import { cleanup, configure, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Consultation, Inspection, Suggestion } from '../lib/types'
import { AssistantPanel } from './AssistantPanel'

configure({ asyncUtilTimeout: 5000 })

vi.mock('./Party', () => ({
  useParties: () => ({ nameOf: (id: string | null | undefined) => id ?? '—' }),
}))

const inspection = (notes = 1): Inspection => ({
  id: 'i-1', assetId: 'asset', inspectorId: 'inspector', expectedDate: '2026-11-04', status: 'IN_PROGRESS',
  schemaVersion: 'schema-v1', startedAt: '2026-11-04T13:00:00Z', closedAt: null, subsystems: [],
  criteria: ['ELEC', 'PRES'].map((criterionId) => ({
    criterionId, section: 'Subsistemas', subsystem: null, applicable: true, answer: null, evidence: [],
    evaluation: null, observedNonConformities: [],
  })),
  notes: Array.from({ length: notes }, (_, n) => ({
    id: `n-${n}`, criterionId: null, text: 'cable pelado junto al tablero', authorId: 'inspector', recordedAt: '2026-11-04T13:30:00Z',
  })),
})

const suggestion = (id: string, criterionId: string, description: string): Suggestion => ({
  id, criterionId, section: 'Subsistemas', result: 'REJECTED', severity: 'CRITICAL', description,
  rationale: 'La nota describe el problema',
  withoutNotes: { answer: '345 MOhm', result: 'APPROVED', severity: null }, decision: null,
})

const answered = (): Consultation => ({
  id: 'c-1', inspectionId: 'i-1', requestedBy: 'inspector', requestedAt: '2026-11-04T14:03:00Z',
  assistant: { adapter: 'openai-compatible', model: 'llama3.2:3b', prompt: 'suggest-findings/v1' },
  suggestions: [suggestion('s1', 'ELEC', 'Conductor expuesto junto al tablero'), suggestion('s2', 'PRES', 'Etiqueta de la válvula borrosa')],
  rejected: [{ criterionId: 'DOC', result: 'APPROVED', severity: 'LOW', description: 'todo bien', rationale: '-', reason: 'an approving result is not a finding' }],
})

let assistant: { enabled: boolean; provider: string }
let consultations: Consultation[]
let failConsultation: string[] | null
let fetchMock: ReturnType<typeof vi.fn>

function reply(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
}

function bodyOf(pathEnd: string): unknown {
  const call = fetchMock.mock.calls.filter(([input]) => String(input).endsWith(pathEnd)).at(-1)
  return JSON.parse(String(call?.[1]?.body))
}

beforeEach(() => {
  assistant = { enabled: true, provider: 'openai-compatible' }
  consultations = []
  failConsultation = null
  fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    const method = init?.method ?? 'GET'
    if (path === '/api/meta/assistant') return reply(assistant)
    if (path === '/api/inspections/i-1/consultations' && method === 'POST') {
      if (failConsultation) {
        return reply({ status: 503, code: 'ASSISTANT_UNAVAILABLE', message: 'unavailable', details: failConsultation }, 503)
      }
      consultations = [answered()]
      return reply(consultations[0], 201)
    }
    if (path === '/api/inspections/i-1/consultations') return reply(consultations)
    const decision = path.match(/consultations\/c-1\/suggestions\/(s\d)\/decision$/)
    if (decision && method === 'POST') {
      const body = JSON.parse(String(init?.body))
      consultations = [{
        ...consultations[0],
        suggestions: consultations[0].suggestions.map((s) => s.id === decision[1]
          ? { ...s, decision: { verdict: body.verdict, by: 'inspector', at: '2026-11-04T14:05:00Z', observationId: 'o-1',
            criterionId: body.criterionId ?? null, result: body.result ?? null, severity: body.severity ?? null,
            description: body.description ?? null, note: body.note ?? null, reason: body.reason ?? null } }
          : s),
      }]
      return reply(consultations[0])
    }
    return reply({})
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

async function consult(onChange = vi.fn()) {
  render(<AssistantPanel inspection={inspection()} onChange={onChange} />)
  fireEvent.click(await screen.findByRole('button', { name: 'Pedir sugerencias' }))
  await screen.findByText('Conductor expuesto junto al tablero')
  return onChange
}

describe('AssistantPanel', () => {
  it('says the assistant is off instead of offering it', async () => {
    assistant = { enabled: false, provider: 'none' }
    render(<AssistantPanel inspection={inspection()} onChange={vi.fn()} />)

    expect(await screen.findByText(/está apagado en este servidor/)).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Pedir sugerencias' })).toBeNull()
    expect(fetchMock).not.toHaveBeenCalledWith('/api/inspections/i-1/consultations', expect.anything())
  })

  it('asks for a note before consulting, since the notes are what the assistant reads', async () => {
    render(<AssistantPanel inspection={inspection(0)} onChange={vi.fn()} />)

    const button = await screen.findByRole('button', { name: 'Pedir sugerencias' })
    expect(button.hasAttribute('disabled')).toBe(true)
    expect(screen.getByText(/Agregá al menos una nota/)).toBeTruthy()
  })

  it('shows the suggestions, which model answered and what the system refused', async () => {
    await consult()

    expect(screen.getByText('Etiqueta de la válvula borrosa')).toBeTruthy()
    expect(screen.getByText('llama3.2:3b')).toBeTruthy()
    expect(screen.getByText('suggest-findings/v1')).toBeTruthy()
    expect(screen.getByText(/Respuestas descartadas por el sistema \(1\)/)).toBeTruthy()
    expect(screen.getAllByText('Sugerencia de IA')).toHaveLength(2)
  })

  it('puts what the notes said first and what the rule alone concluded below', async () => {
    await consult()

    const card = screen.getByRole('article', { name: 'Sugerencia sobre ELEC' })
    const text = card.textContent ?? ''
    expect(text.indexOf('Lo que leyó en las notas')).toBeLessThan(text.indexOf('Sin la nota'))
    expect(text).toContain('con la respuesta «345 MOhm», el criterio quedaba')
    expect(text).toContain('Se registraría como: Conductor expuesto junto al tablero')
  })

  it('accepts a suggestion through the API and refreshes the inspection', async () => {
    const onChange = await consult()

    fireEvent.click(screen.getAllByRole('button', { name: 'Aceptar' })[0])

    await waitFor(() => expect(onChange).toHaveBeenCalled())
    expect(bodyOf('/suggestions/s1/decision')).toEqual({ verdict: 'ACCEPTED' })
    expect(await screen.findByText('Aceptada')).toBeTruthy()
  })

  it('edits a suggestion only with a note explaining why', async () => {
    await consult()

    fireEvent.click(screen.getAllByRole('button', { name: 'Editar' })[0])
    const save = screen.getByRole('button', { name: 'Registrar versión editada' })
    expect(save.hasAttribute('disabled')).toBe(true)
    fireEvent.change(screen.getByLabelText('Resultado'), { target: { value: 'OBSERVED' } })
    fireEvent.change(screen.getByLabelText('Severidad'), { target: { value: 'MEDIUM' } })
    fireEvent.change(screen.getByLabelText(/Por qué la editás/), { target: { value: 'gastada, no cortada' } })
    fireEvent.click(save)

    await waitFor(() => expect(bodyOf('/suggestions/s1/decision')).toEqual({
      verdict: 'EDITED', criterionId: 'ELEC', result: 'OBSERVED', severity: 'MEDIUM',
      description: 'Conductor expuesto junto al tablero', note: 'gastada, no cortada',
    }))
    expect(await screen.findByText('Editada')).toBeTruthy()
  })

  it('discards a suggestion with an optional reason and records nothing else', async () => {
    await consult()

    fireEvent.click(screen.getAllByRole('button', { name: 'Descartar' })[1])
    fireEvent.change(screen.getByLabelText('Motivo (opcional)'), { target: { value: 'la etiqueta se lee bien' } })
    fireEvent.click(screen.getByRole('button', { name: 'Descartar sugerencia' }))

    await waitFor(() => expect(bodyOf('/suggestions/s2/decision')).toEqual({ verdict: 'DISCARDED', reason: 'la etiqueta se lee bien' }))
    expect(await screen.findByText('Motivo: la etiqueta se lee bien')).toBeTruthy()
  })

  it('explains an unavailable assistant in words and keeps nothing', async () => {
    failConsultation = ['UNREACHABLE']
    render(<AssistantPanel inspection={inspection()} onChange={vi.fn()} />)

    fireEvent.click(await screen.findByRole('button', { name: 'Pedir sugerencias' }))

    expect(await screen.findByText(/no respondió a tiempo.*No se registró nada/)).toBeTruthy()
    expect(screen.queryByText('Sugerencia de IA')).toBeNull()
  })

  it('never closes the inspection or issues a certificate on its own', async () => {
    await consult()
    fireEvent.click(screen.getAllByRole('button', { name: 'Aceptar' })[0])
    await screen.findByText('Aceptada')

    const paths = fetchMock.mock.calls.map(([input]) => String(input))
    expect(paths.some((path) => path.endsWith('/close') || path.includes('/certificates'))).toBe(false)
  })
})
