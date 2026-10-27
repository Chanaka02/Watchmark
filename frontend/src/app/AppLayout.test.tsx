import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { json, mockApi, noContent, problem, setCsrfCookie, signedOut, testUser } from '../test/mockApi'
import { renderRoute } from '../test/renderRoute'

describe('app shell', () => {
  it('offers sign-in and registration to signed-out visitors', async () => {
    mockApi({ 'GET /api/v1/auth/me': signedOut })
    renderRoute('/')

    const nav = await screen.findByRole('navigation', { name: 'Account' })
    expect(nav).toHaveTextContent('Sign in')
    expect(nav).toHaveTextContent('Create account')
    expect(screen.getByRole('heading', { name: 'Track every episode.' })).toBeInTheDocument()
  })

  it('signs out, clears the session view and returns home', async () => {
    setCsrfCookie()
    const api = mockApi({
      'GET /api/v1/auth/me': () => json(200, testUser),
      'POST /api/v1/auth/logout': () => noContent(),
    })
    const { router } = renderRoute('/does-not-exist')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /sign out/i }))

    expect(await screen.findByRole('heading', { name: 'Track every episode.' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
    expect(api.calls('POST /api/v1/auth/logout')[0].headers.get('X-XSRF-TOKEN')).toBe('test-csrf-token')
  })

  it('shows an error when the backend is unavailable', async () => {
    mockApi({ 'GET /api/v1/auth/me': () => problem(503, 'unavailable', 'Service unavailable.') })
    renderRoute('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('Watchmark is unavailable right now')
  })

  it('renders a not-found page for unknown paths', async () => {
    mockApi({ 'GET /api/v1/auth/me': signedOut })
    renderRoute('/does-not-exist')

    expect(await screen.findByRole('heading', { name: /page not found/i })).toBeInTheDocument()
  })
})
