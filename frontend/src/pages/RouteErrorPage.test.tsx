import { screen, within } from '@testing-library/react'
import type { RouteObject } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AppLayout } from '../app/AppLayout'
import { signedInRoutes } from '../test/catalogFixtures'
import { mockApi } from '../test/mockApi'
import { renderRoute } from '../test/renderRoute'
import { RouteErrorPage } from './RouteErrorPage'

function failingPage(message: string): RouteObject[] {
  function Broken(): never {
    throw new Error(message)
  }
  return [{ element: <AppLayout />, children: [{ errorElement: <RouteErrorPage />, children: [{ index: true, element: <Broken /> }] }] }]
}

describe('RouteErrorPage', () => {
  beforeEach(() => {
    // The page logs the error for debugging; keep test output quiet.
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  it('replaces a crashed page but keeps the header and navigation', async () => {
    mockApi(signedInRoutes())
    renderRoute('/', failingPage('Cannot read properties of undefined'))

    const alert = await screen.findByRole('alert')
    expect(within(alert).getByRole('heading', { name: 'Something went wrong' })).toBeInTheDocument()
    expect(alert).not.toHaveTextContent('Cannot read properties')
    expect(within(alert).getByRole('button', { name: 'Reload' })).toBeInTheDocument()
    expect(within(alert).getByRole('link', { name: 'Back to home' })).toHaveAttribute('href', '/')
    expect(await screen.findByRole('navigation', { name: 'Main' })).toBeInTheDocument()
  })

  it('suggests reloading when a newer version replaced a page file', async () => {
    mockApi(signedInRoutes())
    renderRoute('/', failingPage('Failed to fetch dynamically imported module: /assets/StatsPage-old.js'))

    expect(await screen.findByText(/Watchmark was updated while this page was open/)).toBeInTheDocument()
  })

  it('shows the not-found page for unknown addresses', async () => {
    mockApi(signedInRoutes())
    renderRoute('/no/such/page')

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})
