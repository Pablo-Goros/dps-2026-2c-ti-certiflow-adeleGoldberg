import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, describeError, query, setActor } from './api'

function reply(status: number, body: unknown) {
  return Promise.resolve(new Response(body === null ? null : JSON.stringify(body), { status }))
}

describe('api client', () => {
  const fetchMock = vi.fn()
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    setActor(null)
  })
  afterEach(() => {
    fetchMock.mockReset()
    vi.unstubAllGlobals()
  })

  it('sends JSON and the actor header, and parses the answer', async () => {
    fetchMock.mockReturnValue(reply(201, { id: 'p1' }))
    setActor('ana')

    const created = await api.post<{ id: string }>('/parties', { name: 'Ana' })

    expect(created.id).toBe('p1')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/parties')
    expect(init.method).toBe('POST')
    expect(init.headers['X-Actor-Id']).toBe('ana')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe('{"name":"Ana"}')
  })

  it('does not send an actor header when nobody is chosen', async () => {
    fetchMock.mockReturnValue(reply(200, []))

    await api.get('/parties')

    expect(fetchMock.mock.calls[0][1].headers['X-Actor-Id']).toBeUndefined()
  })

  it('turns an error body into an ApiError with code and details', async () => {
    fetchMock.mockReturnValue(reply(422, { status: 422, code: 'BUSINESS_RULE', message: 'No se puede', details: ['a', 'b'] }))

    const failure = (await api.get('/x').catch((e) => e)) as ApiError

    expect(failure).toBeInstanceOf(ApiError)
    expect(failure.status).toBe(422)
    expect(failure.code).toBe('BUSINESS_RULE')
    expect(failure.details).toEqual(['a', 'b'])
  })

  it('keeps the whole body of a blocked issuance', async () => {
    fetchMock.mockReturnValue(reply(422, { outcome: 'BLOCKED', assessment: { blockers: [{ type: 'InspectionNotClosed' }] } }))

    const failure = (await api.post('/inspections/i/certificates').catch((e) => e)) as ApiError

    expect((failure.body as { outcome: string }).outcome).toBe('BLOCKED')
  })

  it('reports an unreachable server in plain words', async () => {
    fetchMock.mockRejectedValue(new TypeError('failed'))

    const failure = (await api.get('/x').catch((e) => e)) as ApiError

    expect(failure.code).toBe('NETWORK')
    expect(describeError(failure)).toMatch(/conectar/)
  })

  it('tells the person to choose who they are on a 401', () => {
    expect(describeError(new ApiError(401, 'ACTOR_REQUIRED', 'x'))).toMatch(/quién sos/)
  })

  it('handles empty bodies (204)', async () => {
    fetchMock.mockReturnValue(reply(204, null))
    expect(await api.del('/x')).toBeNull()
  })

  it('builds query strings from the filters that have a value', () => {
    expect(query({ a: 'x', b: '', c: undefined, d: true })).toBe('?a=x&d=true')
    expect(query({})).toBe('')
  })
})
