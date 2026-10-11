// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CertificationPanel } from './CertificationPanel'

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = new URL(String(input), 'http://localhost')
    if (url.pathname === '/api/certificates') return new Response('[]', { status: 200 })
    if (url.pathname.endsWith('/global-derivation')) {
      return new Response(JSON.stringify({ type: 'Derived' }), { status: 200 })
    }
    if (url.pathname.endsWith('/eligibility')) {
      return new Response(JSON.stringify({ blockers: [], mode: 'REGULAR' }), { status: 200 })
    }
    return new Response('{}', { status: 200 })
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('CertificationPanel for a by-parts inspection', () => {
  it('shows the derived global result without requesting direct whole-asset eligibility', async () => {
    render(
      <MemoryRouter>
        <CertificationPanel inspectionId="inspection" assetId="asset" subsystems={['PRESSURE', 'SAFETY']} />
      </MemoryRouter>,
    )

    expect(await screen.findByText('Los certificados parciales vigentes componen un certificado global.')).toBeTruthy()
    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    const requested = fetchMock.mock.calls.map(([input]) => String(input))
    expect(requested).not.toContain('/api/inspections/inspection/eligibility')
    expect(screen.queryByText('Certificado global')).toBeNull()
  })
})
