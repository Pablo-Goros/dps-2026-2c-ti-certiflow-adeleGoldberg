// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { Inspection, InspectionAct, Schema } from '../lib/types'
import { InspectionDetail } from './InspectionDetail'

vi.mock('../components/Party', () => ({
  useParties: () => ({ nameOf: (id: string | null | undefined) => id ?? '—' }),
}))

const line = {
  criterionId: 'TEMP', section: 'Mediciones', subsystem: null, applicable: true,
  answer: { type: 'MEASUREMENT' as const, value: 30, unit: '°C' }, evidence: [],
  evaluation: { result: 'REJECTED' as const, severity: 'HIGH' as const, reasons: ['Fuera de rango'], fromRectification: false },
}

const schema: Schema = {
  id: 'schema', name: 'Laboratorio', assetTypes: ['LABORATORY'], draft: null, effectiveVersion: 1,
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
  closedAt: '2026-10-10T11:00:00Z', schemaVersionId: 'schema-v1',
  sections: [{ name: 'Mediciones', lines: [{
    criterionId: 'TEMP', applicable: true,
    answer: { type: 'Rectified', originalValue: '30 °C', correctedValue: '5 °C', reason: 'Lectura mal transcripta' },
    evidenceReferences: [], missingEvidence: [],
    result: { type: 'Rectified', originalValue: 'REJECTED', correctedValue: 'APPROVED', reason: 'Lectura mal transcripta' },
    severity: null, reasons: [],
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
    fireEvent.change(screen.getByLabelText('Nueva medición de TEMP'), { target: { value: '5' } })
    fireEvent.change(screen.getByLabelText('Motivo de rectificación'), { target: { value: 'Lectura mal transcripta' } })
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
    expect(fetchMock).toHaveBeenCalledWith('/api/inspections/inspection/act', expect.anything())
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
