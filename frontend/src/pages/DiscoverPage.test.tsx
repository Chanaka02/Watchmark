import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { page, signedInRoutes, summary } from '../test/catalogFixtures'
import { json, mockApi, problem, signedOut } from '../test/mockApi'
import { renderRoute } from '../test/renderRoute'

describe('DiscoverPage', () => {
  it('shows trending shows by default with posters, years and ratings', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/v1/catalog/discover/trending?page=1': () =>
        json(200, page([summary(1, { name: 'Fixture Harbour', voteAverage: 8.14, posterUrl: 'https://img/p.jpg' }), summary(2, { firstAirDate: null })])),
    })
    renderRoute('/discover')

    const link = await screen.findByRole('link', { name: /fixture harbour/i })
    expect(link).toHaveAttribute('href', '/shows/tmdb:1')
    expect(within(link).getByText('2021')).toBeInTheDocument()
    expect(within(link).getByText('8.1')).toBeInTheDocument()
    expect(within(screen.getByRole('link', { name: /show 2/i })).getByText('Date unknown')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Trending' })).toHaveAttribute('aria-current', 'page')
  })

  it('switches lists', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/v1/catalog/discover/trending?page=1': () => json(200, page([summary(1)])),
      'GET /api/v1/catalog/discover/top-rated?page=1': () => json(200, page([summary(9, { name: 'Best Fixture' })])),
    })
    renderRoute('/discover')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: 'Top rated' }))

    expect(await screen.findByRole('link', { name: /best fixture/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Top rated' })).toHaveAttribute('aria-current', 'page')
  })

  it('loads more pages and stops at the last one', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/v1/catalog/discover/popular?page=1': () => json(200, page([summary(1), summary(2)], 1, 2, 3)),
      // The provider repeats show 2 on page 2: it must not appear twice.
      'GET /api/v1/catalog/discover/popular?page=2': () => json(200, page([summary(2), summary(3)], 2, 2, 3)),
    })
    renderRoute('/discover?list=popular')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Load more' }))

    expect(await screen.findByRole('link', { name: /show 3/i })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /show 2/i })).toHaveLength(1)
    expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument()
  })

  it('shows an empty state', async () => {
    mockApi({ ...signedInRoutes(), 'GET /api/v1/catalog/discover/on-the-air?page=1': () => json(200, page([], 1, 0, 0)) })
    renderRoute('/discover?list=on-the-air')

    expect(await screen.findByText('Nothing here right now')).toBeInTheDocument()
  })

  it('shows an error with a working retry', async () => {
    const discover = vi
      .fn()
      .mockReturnValueOnce(problem(503, 'metadata-unavailable', 'Show information is temporarily unavailable.'))
      .mockReturnValueOnce(json(200, page([summary(5, { name: 'Recovered Fixture' })])))
    mockApi({ ...signedInRoutes(), 'GET /api/v1/catalog/discover/trending?page=1': discover })
    renderRoute('/discover')
    const user = userEvent.setup()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Show information is temporarily unavailable.')
    await user.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('link', { name: /recovered fixture/i })).toBeInTheDocument()
  })

  it('labels mock data clearly', async () => {
    mockApi({
      ...signedInRoutes({ provider: 'mock', mock: true }),
      'GET /api/v1/catalog/discover/trending?page=1': () => json(200, page([summary(1)])),
    })
    renderRoute('/discover')

    expect(await screen.findByText(/these shows are fictional/i)).toBeInTheDocument()
  })

  it('requires sign-in', async () => {
    mockApi({ 'GET /api/v1/auth/me': signedOut, 'GET /api/v1/catalog/source': () => json(200, { provider: 'tmdb', mock: false }) })
    const { router } = renderRoute('/discover')

    await screen.findByRole('heading', { name: 'Sign in' })
    expect(router.state.location.search).toBe('?next=%2Fdiscover')
  })
})
