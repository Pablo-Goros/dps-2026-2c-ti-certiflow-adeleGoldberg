// The only place that talks HTTP. Every call returns parsed JSON or throws an ApiError.

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: string[]
  /** The whole response body, for answers that are not plain errors (a blocked issuance carries its assessment). */
  readonly body: unknown

  constructor(status: number, code: string, message: string, details: string[] = [], body: unknown = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
    this.body = body
  }
}

const ACTOR_KEY = 'certiflow.actor'
let actorId: string | null = readStoredActor()

function readStoredActor(): string | null {
  try {
    return globalThis.localStorage?.getItem(ACTOR_KEY) ?? null
  } catch {
    return null
  }
}

export function currentActor(): string | null {
  return actorId
}

export function setActor(id: string | null): void {
  actorId = id
  try {
    if (id) globalThis.localStorage?.setItem(ACTOR_KEY, id)
    else globalThis.localStorage?.removeItem(ACTOR_KEY)
  } catch {
    // Private mode or blocked storage: the choice just lasts until the page is closed.
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (actorId) headers['X-Actor-Id'] = actorId

  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'NETWORK', 'No se pudo conectar con el servidor.')
  }

  const text = await response.text()
  const parsed: unknown = text ? safeParse(text) : null
  if (!response.ok) {
    const error = (parsed ?? {}) as { code?: string; message?: string; details?: string[] }
    throw new ApiError(
      response.status,
      error.code ?? 'HTTP_' + response.status,
      error.message ?? `Error ${response.status}`,
      error.details ?? [],
      parsed,
    )
  }
  return parsed as T
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body: unknown = {}) => request<T>('POST', path, body),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
}

/** Builds a query string from the filters that actually have a value. */
export function query(params: Record<string, string | boolean | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

/** What to show a person when a call fails. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Elegí quién sos en el selector de arriba para hacer esto.'
    const extra = error.details.length ? ` ${error.details.join('; ')}` : ''
    return error.message + extra
  }
  return error instanceof Error ? error.message : 'Ocurrió un error inesperado.'
}
