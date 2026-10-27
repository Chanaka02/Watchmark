/**
 * Same-origin JSON client. Every feature calls the backend through this module,
 * so credentials, CSRF and error mapping are handled in exactly one place.
 *
 * CSRF (double-submit cookie): the backend sets a readable `XSRF-TOKEN` cookie;
 * state-changing requests echo it in the `X-XSRF-TOKEN` header. The token is
 * rotated on login and cleared on logout, so a missing cookie is fetched on
 * demand, and a single rejected token is refreshed and the request retried once.
 */

export type FieldError = { field: string; message: string }

export class ApiError extends Error {
  readonly status: number
  /** Stable problem slug from the backend, e.g. `invalid-credentials`; `network` when unreachable. */
  readonly type: string
  readonly fieldErrors: FieldError[]
  readonly retryAfterSeconds: number | null

  constructor(
    status: number,
    type: string,
    message: string,
    fieldErrors: FieldError[] = [],
    retryAfterSeconds: number | null = null,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.type = type
    this.fieldErrors = fieldErrors
    this.retryAfterSeconds = retryAfterSeconds
  }
}

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

type RequestOptions = { body?: unknown; signal?: AbortSignal }

export function apiGet<T>(path: string, options?: RequestOptions) {
  return apiRequest<T>('GET', path, options)
}

export function apiPost<T>(path: string, options?: RequestOptions) {
  return apiRequest<T>('POST', path, options)
}

export function apiPatch<T>(path: string, options?: RequestOptions) {
  return apiRequest<T>('PATCH', path, options)
}

export function apiPut<T>(path: string, options?: RequestOptions) {
  return apiRequest<T>('PUT', path, options)
}

export function apiDelete<T>(path: string, options?: RequestOptions) {
  return apiRequest<T>('DELETE', path, options)
}

export async function apiRequest<T>(method: Method, path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(method, path, options)
  if (response.ok) {
    return (response.status === 204 ? undefined : await response.json()) as T
  }

  const error = await toApiError(response)
  if (error.type === 'csrf-token-invalid' && !SAFE_METHODS.has(method)) {
    // The token may have rotated (login/logout in another tab): refresh once.
    await refreshCsrfToken()
    const retry = await send(method, path, options)
    if (retry.ok) {
      return (retry.status === 204 ? undefined : await retry.json()) as T
    }
    throw await toApiError(retry)
  }
  throw error
}

async function send(method: Method, path: string, { body, signal }: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (!SAFE_METHODS.has(method)) {
    headers[CSRF_HEADER] = readCookie(CSRF_COOKIE) ?? (await refreshCsrfToken())
  }
  try {
    return await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
      signal,
    })
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause
    }
    throw new ApiError(0, 'network', 'The server could not be reached. Check your connection and try again.')
  }
}

async function refreshCsrfToken(): Promise<string> {
  try {
    await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' })
  } catch {
    throw new ApiError(0, 'network', 'The server could not be reached. Check your connection and try again.')
  }
  const token = readCookie(CSRF_COOKIE)
  if (!token) {
    throw new ApiError(0, 'csrf-unavailable', 'Could not start a secure request. Please reload the page.')
  }
  return token
}

function readCookie(name: string): string | null {
  for (const part of document.cookie.split(';')) {
    const [key, ...value] = part.trim().split('=')
    if (key === name) {
      return decodeURIComponent(value.join('='))
    }
  }
  return null
}

async function toApiError(response: Response): Promise<ApiError> {
  const retryAfter = Number(response.headers.get('Retry-After'))
  let problem: { type?: string; detail?: string; errors?: FieldError[] } = {}
  try {
    problem = await response.json()
  } catch {
    // Non-JSON error (e.g. a proxy page): fall back to the status code.
  }
  const type = problem.type?.replace(/^\/problems\//, '') ?? `http-${response.status}`
  return new ApiError(
    response.status,
    type,
    problem.detail ?? `Request failed with status ${response.status}.`,
    Array.isArray(problem.errors) ? problem.errors : [],
    Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null,
  )
}
