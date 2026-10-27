import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { json, mockApi, problem, signedOut, testUser } from '../../test/mockApi'
import { renderRoute } from '../../test/renderRoute'
import { RequireAuth } from './RequireAuth'

const routes = [
  {
    path: '/private',
    element: (
      <RequireAuth>
        <p>Secret library</p>
      </RequireAuth>
    ),
  },
  { path: '/login', element: <p>Login page</p> },
]

describe('RequireAuth', () => {
  it('redirects signed-out users to login and remembers where they were going', async () => {
    mockApi({ 'GET /api/v1/auth/me': signedOut })
    const { router } = renderRoute('/private?tab=2', routes)

    expect(await screen.findByText('Login page')).toBeInTheDocument()
    expect(screen.queryByText('Secret library')).not.toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toBe('?next=%2Fprivate%3Ftab%3D2')
  })

  it('renders the page for a signed-in user', async () => {
    mockApi({ 'GET /api/v1/auth/me': () => json(200, testUser) })
    renderRoute('/private', routes)

    expect(await screen.findByText('Secret library')).toBeInTheDocument()
  })

  it('shows an error instead of redirecting when the session check fails', async () => {
    mockApi({ 'GET /api/v1/auth/me': () => problem(500, 'internal-error', 'Something went wrong.') })
    renderRoute('/private', routes)

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not check your session')
    expect(screen.queryByText('Login page')).not.toBeInTheDocument()
  })
})
