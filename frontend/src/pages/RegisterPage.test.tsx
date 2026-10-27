import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'

import { json, mockApi, problem, setCsrfCookie, signedOut, testUser } from '../test/mockApi'
import { renderRoute } from '../test/renderRoute'

const GOOD_PASSWORD = 'correct horse battery staple'

async function fillAndSubmit({ name = 'Ana', email = 'ana@example.com', password = GOOD_PASSWORD } = {}) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Display name'), name)
  await user.type(screen.getByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Create account' }))
}

describe('RegisterPage', () => {
  beforeEach(() => setCsrfCookie())

  it('enforces the password policy before calling the API', async () => {
    const api = mockApi({ 'GET /api/v1/auth/me': signedOut })
    renderRoute('/register')

    await fillAndSubmit({ password: 'too short' })

    expect(screen.getByText('Use at least 15 characters.')).toBeInTheDocument()
    expect(api.calls('POST /api/v1/auth/register')).toHaveLength(0)
  })

  it('rejects an invalid email', async () => {
    const api = mockApi({ 'GET /api/v1/auth/me': signedOut })
    renderRoute('/register')

    await fillAndSubmit({ email: 'not-an-email' })

    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument()
    expect(api.calls('POST /api/v1/auth/register')).toHaveLength(0)
  })

  it('creates the account with the browser time zone and signs in', async () => {
    const api = mockApi({
      'GET /api/v1/auth/me': signedOut,
      'POST /api/v1/auth/register': () => json(201, testUser),
    })
    renderRoute('/register')

    await fillAndSubmit({ name: '  Ana  ' })

    expect(await screen.findByRole('heading', { name: 'Welcome back, Ana.' })).toBeInTheDocument()
    expect(api.calls('POST /api/v1/auth/register')[0].body).toEqual({
      displayName: 'Ana',
      email: 'ana@example.com',
      password: GOOD_PASSWORD,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    })
  })

  it('shows a taken email next to the email field', async () => {
    mockApi({
      'GET /api/v1/auth/me': signedOut,
      'POST /api/v1/auth/register': () =>
        problem(409, 'email-taken', 'An account with this email already exists.'),
    })
    renderRoute('/register')

    await fillAndSubmit()

    expect(await screen.findByText('An account with this email already exists.')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
  })

  it('maps backend validation errors onto fields', async () => {
    mockApi({
      'GET /api/v1/auth/me': signedOut,
      'POST /api/v1/auth/register': () =>
        problem(400, 'validation-failed', 'One or more fields are invalid.', {
          errors: [{ field: 'password', message: 'must be at least 15 characters and at most 72 bytes' }],
        }),
    })
    renderRoute('/register')

    await fillAndSubmit()

    expect(
      await screen.findByText('Must be at least 15 characters and at most 72 bytes.'),
    ).toBeInTheDocument()
  })
})
