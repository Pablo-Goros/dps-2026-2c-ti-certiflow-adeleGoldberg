// @vitest-environment jsdom
import { cleanup, configure, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { Inspection, InspectionAct, ObservedNonConformity, Schema } from '../lib/types'
import { InspectionDetail } from './InspectionDetail'

// The page loads several resources before it renders; a slow CI runner needs more than the 1 s default.
configure({ asyncUtilTimeout: 5000 })

vi.mock('../components/Party', () => ({
  useParties: () => ({ nameOf: (id: string | null | undefined) => id ?? '—' }),
}))

const line = {
  criterionId: 'TEMP', section: 'Mediciones', subsystem: null, applicable: true,
  answer: { type: 'MEASUREMENT' as const, value: 30, unit: '°C' }, evidence: [],
  evaluation: { result: 'REJECTED' as const, severity: 'HIGH' as const, reasons: ['Fuera de rango'], fromRectification: false },
  observedNonConformities: [] as ObservedNonConformity[],
}

const schema: Schema = {
  id: 'schema', name: 'Laboratorio', assetTypes: ['LABORATORY'], draft: null, effectiveVersion: 1,
  withdrawals: [], // <-- Propiedad agregada para satisfacer la interfaz Schema
  versions: [{
    id: 'schema-v1', number: 1, publishedAt: '2026-01-01T00:00:00Z', effectiveFrom: '2026-01-01T00:00:00Z',
    sections: [{ name: 'Mediciones', order: 1, criteria: [{
        id: 'TEMP', subsystem: null, evidence: [],
        rule: { type: 'NUMERIC_RANGE', unit: '°C', minimum: 2, maximum: 8, bands: [] },
      }] }],
  }],
}

const closedInspection = (): Inspection => ({
  id: 'inspection', assetId: 'asset', inspectorId: 'inspector', expectedDate: '2026-10-10',
  status: 'CLOSED', schemaVersion: 'schema-v1', startedAt: '2026-10-10T10:00:00Z', closedAt: '2026-10-10T11:00:00Z',
  subsystems: [], criteria: [{ ...line }], notes: [],
})

const inProgressWithNoAnswers = (): Inspection => ({
  ...closedInspection(), status: 'IN_PROGRESS', closedAt: null,
  criteria: [{ ...line, answer: null, evaluation: null }],
})

const act: InspectionAct = {
  inspectionId: 'inspection', status: 'CLOSED',
  asset: { name: 'Laboratorio histórico', location: 'Piso 2' },
  inspector: 'inspector', expectedDate: '2026-10-10', startedAt: '2026-10-10T10:00:00Z',
  closedAt: '2026-10-10T11:00:00Z', schemaVersionId: { schemaId: 'schema', number: 1 },
  sections: [{ name: 'Mediciones', lines: [{
    criterionId: 'TEMP', applicable: true,
    answer: { type: 'Rectified', originalValue: '30 °C', correctedValue: '5 °C', reason: 'Lectura mal transcripta' },
    evidenceReferences: [], missingEvidence: [],
    result: { type: 'Rectified', originalValue: 'REJECTED', correctedValue: 'APPROVED', reason: 'Lectura mal transcripta' },
    severity: null, reasons: [],
    observations: [{
      id: 'o-1', result: 'OBSERVED', severity: 'MEDIUM', description: 'Sensor mal fijado', recordedBy: 'inspector',
      recordedAt: '2026-10-10T10:40:00Z', origin: { consultationId: 'c-1', suggestionId: 's1', edited: true, assistant: 'test m v1' },
    }],
  }] }],
  notes: [], rectifications: [{ author: 'inspector', performedAt: '2026-10-10T12:00:00Z', reason: 'Lectura mal transcripta', changes: ['TEMP: 30 °C → 5 °C'] }],
}

let inspection: Inspection
let fetchMock: ReturnType<typeof vi.fn>

function reply(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/inspecciones/inspection']}>
      <Routes><Route path="/inspecciones/:id" element={<InspectionDetail />} /></Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  inspection = closedInspection()
  fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = new URL(String(input), 'http://localhost').pathname + new URL(String(input), 'http://localhost').search
    if (path === '/api/inspections/inspection/close' && init?.method === 'POST') {
      inspection = { ...inspection, status: 'CLOSED', closedAt: '2026-10-10T11:00:00Z' }
      return reply(inspection)
    }
    if (path === '/api/inspections/inspection/rectifications' && init?.method === 'POST') return reply(inspection, 201)
    if (path === '/api/inspections/inspection') return reply(inspection)
    if (path === '/api/inspections/inspection/act') return reply(act)
    if (path === '/api/assets/asset') return reply({ id: 'asset', name: 'Laboratorio', subsystems: [], assetType: 'LABORATORY' })
    if (path === '/api/schemas') return reply([schema])
    if (path.startsWith('/api/certificates')) return reply([])
    if (path.endsWith('/global-derivation')) return reply({ type: 'NotDerivable', reasons: [] })
    if (path.includes('/eligibility')) return reply({ blockers: [], mode: 'REGULAR' })
    return reply({})
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('InspectionDetail closed and incomplete workflows', () => {
  it('rectifies a closed result through the documented API with a reason', async () => {
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Rectificar inspección' }))
    fireEvent.change(await screen.findByLabelText('Nueva medición de TEMP'), { target: { value: '5' } })
    fireEvent.change(await screen.findByLabelText('Motivo de rectificación'), { target: { value: 'Lectura mal transcripta' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar rectificación' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/inspections/inspection/rectifications', expect.objectContaining({ method: 'POST' })))
    const request = fetchMock.mock.calls.find(([input]) => String(input).endsWith('/rectifications'))?.[1]
    expect(JSON.parse(String(request?.body))).toEqual({
      reason: 'Lectura mal transcripta',
      corrections: [{ type: 'ANSWER', criterionId: 'TEMP', answer: { type: 'MEASUREMENT', value: 5, unit: '°C' } }],
    })
  })

  it('loads and presents the inspection act with original and corrected values', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Acta de inspección' })).toBeTruthy()
    expect(screen.getAllByText('Laboratorio histórico').length).toBeGreaterThan(0)
    expect(screen.getAllByText('30 °C').length).toBeGreaterThan(0)
    expect(screen.getAllByText('5 °C').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Lectura mal transcripta').length).toBeGreaterThan(0)
    expect(screen.getAllByText(/Sensor mal fijado/).length).toBeGreaterThan(0)
    expect(screen.getByText('Sugerida por IA, editada')).toBeTruthy()
    expect(screen.getByText('Versión aplicada')).toBeTruthy()
    expect(screen.getByText('v1')).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledWith('/api/inspections/inspection/act', expect.anything())
  })

  it('shows who proposed each observed non-conformity, and records and withdraws them through the API', async () => {
    const observation = (id: string, description: string, origin: ObservedNonConformity['origin']): ObservedNonConformity => ({
      id, criterionId: 'TEMP', result: 'REJECTED', severity: 'CRITICAL', description, noteId: null,
      recordedBy: 'inspector', recordedAt: '2026-10-10T10:30:00Z', origin,
    })
    inspection = {
      ...inProgressWithNoAnswers(),
      criteria: [{
        ...line, answer: null, evaluation: null,
        observedNonConformities: [
          observation('o-ai', 'Conductor expuesto', { consultationId: 'c-1', suggestionId: 's1', edited: false, assistant: 'test m v1' }),
          observation('o-own', 'Tapa del tablero rota', null),
        ],
      }],
    }
    renderPage()

    expect(await screen.findByText('Sugerida por IA')).toBeTruthy()
    expect(screen.getByText('Del inspector')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Registrar no conformidad' }))
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Falta señalización' } })
    fireEvent.click(screen.getByRole('button', { name: 'Registrar' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/inspections/inspection/criteria/TEMP/observed-nonconformities', expect.objectContaining({ method: 'POST' })))
    const recorded = fetchMock.mock.calls.find(([input, init]) =>
      String(input).endsWith('/observed-nonconformities') && init?.method === 'POST')?.[1]
    expect(JSON.parse(String(recorded?.body))).toEqual({ result: 'OBSERVED', severity: 'MEDIUM', description: 'Falta señalización' })

    fireEvent.click(screen.getAllByRole('button', { name: 'Quitar' })[0])
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/inspections/inspection/criteria/TEMP/observed-nonconformities/o-ai', expect.objectContaining({ method: 'DELETE' })))
  })

  it('allows closure when every applicable answer is missing', async () => {
    inspection = inProgressWithNoAnswers()
    renderPage()

    const close = await screen.findByRole('button', { name: 'Cerrar inspección' })
    expect(close.hasAttribute('disabled')).toBe(false)
    fireEvent.click(close)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/inspections/inspection/close', expect.objectContaining({ method: 'POST' })))
  })
})
