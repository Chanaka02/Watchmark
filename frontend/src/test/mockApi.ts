import { vi } from 'vitest'

type Handler = (request: { body: unknown; headers: Headers }) => Response | Promise<Response>

/**
 * Routes `fetch` calls by `"METHOD /path"` to handlers. Any request without a
 * handler fails the test loudly instead of surfacing as a vague network error.
 */
export function mockApi(routes: Record<string, Handler>) {
  const unexpected: string[] = []
  const spy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.pathname : input.url
    const key = `${(init?.method ?? 'GET').toUpperCase()} ${url}`
    const handler = routes[key]
    if (!handler) {
      unexpected.push(key)
      throw new Error(`mockApi: unexpected request ${key}`)
    }
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined
    return handler({ body, headers: new Headers(init?.headers) })
  })
  return {
    spy,
    /** Requests made for `key`, with their parsed JSON bodies. */
    calls(key: string) {
      return spy.mock.calls
        .filter(([input, init]) => `${(init?.method ?? 'GET').toUpperCase()} ${String(input)}` === key)
        .map(([, init]) => ({
          body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
          headers: new Headers(init?.headers),
        }))
    },
    assertNoUnexpectedRequests() {
      if (unexpected.length > 0) {
        throw new Error(`Unexpected requests: ${unexpected.join(', ')}`)
      }
    },
  }
}

export function json(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

export function problem(
  status: number,
  type: string,
  detail: string,
  extra: Record<string, unknown> = {},
  headers: Record<string, string> = {},
) {
  return new Response(JSON.stringify({ type: `/problems/${type}`, title: type, status, detail, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json', ...headers },
  })
}

export const noContent = () => new Response(null, { status: 204 })

export const testUser = { id: 7, email: 'ana@example.com', displayName: 'Ana', timeZone: 'Asia/Colombo' }

export const signedOut = () => problem(401, 'unauthenticated', 'Sign in to continue.')

export function setCsrfCookie(value = 'test-csrf-token') {
  document.cookie = `XSRF-TOKEN=${value}; path=/`
}

export function clearCsrfCookie() {
  document.cookie = 'XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
}
