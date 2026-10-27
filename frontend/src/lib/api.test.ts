import { describe, expect, it, vi } from 'vitest'

import { json, mockApi, noContent, problem, setCsrfCookie } from '../test/mockApi'
import { ApiError, apiGet, apiPost } from './api'

describe('api client', () => {
  it('sends the CSRF cookie value as a header on state-changing requests', async () => {
    setCsrfCookie('abc123')
    const api = mockApi({ 'POST /api/v1/things': () => json(200, { ok: true }) })

    await expect(apiPost('/api/v1/things', { body: { name: 'x' } })).resolves.toEqual({ ok: true })

    const [call] = api.calls('POST /api/v1/things')
    expect(call.headers.get('X-XSRF-TOKEN')).toBe('abc123')
    expect(call.headers.get('Content-Type')).toBe('application/json')
    expect(call.body).toEqual({ name: 'x' })
    expect(api.spy).toHaveBeenCalledWith('/api/v1/things', expect.objectContaining({ credentials: 'same-origin' }))
  })

  it('does not send a CSRF header on GET requests', async () => {
    setCsrfCookie('abc123')
    const api = mockApi({ 'GET /api/v1/things': () => json(200, []) })

    await apiGet('/api/v1/things')

    expect(api.calls('GET /api/v1/things')[0].headers.has('X-XSRF-TOKEN')).toBe(false)
  })

  it('fetches a CSRF token first when the cookie is missing', async () => {
    const api = mockApi({
      'GET /api/v1/auth/csrf': () => {
        setCsrfCookie('fresh-token')
        return noContent()
      },
      'POST /api/v1/things': () => noContent(),
    })

    await expect(apiPost('/api/v1/things')).resolves.toBeUndefined()

    expect(api.calls('GET /api/v1/auth/csrf')).toHaveLength(1)
    expect(api.calls('POST /api/v1/things')[0].headers.get('X-XSRF-TOKEN')).toBe('fresh-token')
  })

  it('refreshes a rejected CSRF token and retries exactly once', async () => {
    setCsrfCookie('stale')
    const post = vi
      .fn()
      .mockReturnValueOnce(problem(403, 'csrf-token-invalid', 'Refresh the page and try again.'))
      .mockReturnValueOnce(json(200, { ok: true }))
    const api = mockApi({
      'GET /api/v1/auth/csrf': () => {
        setCsrfCookie('rotated')
        return noContent()
      },
      'POST /api/v1/things': post,
    })

    await expect(apiPost('/api/v1/things')).resolves.toEqual({ ok: true })

    const calls = api.calls('POST /api/v1/things')
    expect(calls.map((call) => call.headers.get('X-XSRF-TOKEN'))).toEqual(['stale', 'rotated'])
  })

  it('turns problem+json responses into ApiError with type, field errors and Retry-After', async () => {
    setCsrfCookie()
    mockApi({
      'POST /api/v1/things': () =>
        problem(
          429,
          'rate-limited',
          'Too many attempts.',
          { errors: [{ field: 'email', message: 'is invalid' }] },
          { 'Retry-After': '90' },
        ),
    })

    const error = await apiPost('/api/v1/things').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 429,
      type: 'rate-limited',
      message: 'Too many attempts.',
      fieldErrors: [{ field: 'email', message: 'is invalid' }],
      retryAfterSeconds: 90,
    })
  })

  it('reports an unreachable server as a network error', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(apiGet('/api/v1/things')).rejects.toMatchObject({ status: 0, type: 'network' })
  })

  it('handles non-JSON error bodies', async () => {
    mockApi({ 'GET /api/v1/things': () => new Response('<html>Bad gateway</html>', { status: 502 }) })

    await expect(apiGet('/api/v1/things')).rejects.toMatchObject({ status: 502, type: 'http-502' })
  })
})
