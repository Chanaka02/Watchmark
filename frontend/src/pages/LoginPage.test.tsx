import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'

import { json, mockApi, problem, setCsrfCookie, signedOut, testUser } from '../test/mockApi'
import { renderRoute } from '../test/renderRoute'

async function fillAndSubmit(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('LoginPage', () => {
  beforeEach(() => setCsrfCookie())

  it('validates required fields without calling the API', async () => {
    const api = mockApi({ 'GET /api/v1/auth/me': signedOut })
    renderRoute('/login')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Sign in' }))

    expect(screen.getByText('Enter your email address.')).toBeInTheDocument()
    expect(screen.getByText('Enter your password.')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
    expect(api.calls('POST /api/v1/auth/login')).toHaveLength(0)
  })

  it('shows the server message for wrong credentials', async () => {
    mockApi({
      'GET /api/v1/auth/me': signedOut,
      'POST /api/v1/auth/login': () => problem(401, 'invalid-credentials', 'The email or password is incorrect.'),
    })
    renderRoute('/login')

    await fillAndSubmit('ana@example.com', 'wrong password')

    expect(await screen.findByRole('alert')).toHaveTextContent('The email or password is incorrect.')
  })

  it('explains rate limiting with the retry time', async () => {
    mockApi({
      'GET /api/v1/auth/me': signedOut,
      'POST /api/v1/auth/login': () =>
        problem(429, 'rate-limited', 'Too many attempts.', {}, { 'Retry-After': '90' }),
    })
    renderRoute('/login')

    await fillAndSubmit('ana@example.com', 'whatever')

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts. Try again in 2 minutes.')
  })

  it('signs in and continues to the requested page', async () => {
    const api = mockApi({
      'GET /api/v1/auth/me': signedOut,
      'POST /api/v1/auth/login': () => json(200, testUser),
    })
    const { router } = renderRoute('/login?next=%2Flibrary')

    await fillAndSubmit('ana@example.com', 'correct horse battery staple')

    await screen.findByRole('button', { name: /sign out/i })
    expect(router.state.location.pathname).toBe('/library')
    expect(api.calls('POST /api/v1/auth/login')[0].body).toEqual({
      email: 'ana@example.com',
      password: 'correct horse battery staple',
    })
  })

  it('ignores an off-site next parameter', async () => {
    mockApi({
      'GET /api/v1/auth/me': signedOut,
      'POST /api/v1/auth/login': () => json(200, testUser),
    })
    const { router } = renderRoute('/login?next=%2F%2Fevil.example')

    await fillAndSubmit('ana@example.com', 'correct horse battery staple')

    expect(await screen.findByRole('heading', { name: 'Welcome back, Ana.' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('redirects away when already signed in', async () => {
    mockApi({ 'GET /api/v1/auth/me': () => json(200, testUser) })
    const { router } = renderRoute('/login')

    expect(await screen.findByRole('heading', { name: 'Welcome back, Ana.' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('lets the user reveal the password', async () => {
    mockApi({ 'GET /api/v1/auth/me': signedOut })
    renderRoute('/login')
    const user = userEvent.setup()

    const password = await screen.findByLabelText('Password')
    expect(password).toHaveAttribute('type', 'password')
    await user.click(screen.getByRole('button', { name: 'Show password' }))
    expect(password).toHaveAttribute('type', 'text')
  })
})
