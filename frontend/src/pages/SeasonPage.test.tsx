import { screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { details, episode, season, signedInRoutes } from '../test/catalogFixtures'
import { json, mockApi, problem, testUser } from '../test/mockApi'
import { renderRoute } from '../test/renderRoute'

function routes(user = testUser) {
  return {
    ...signedInRoutes(),
    'GET /api/v1/auth/me': () => json(200, user),
    'GET /api/v1/catalog/shows/tmdb%3A1': () => json(200, details()),
    'GET /api/v1/catalog/shows/tmdb%3A1/seasons/1': () =>
      json(
        200,
        season([
          episode(1, { name: 'Arrival', airDate: '2026-10-21', runtimeMinutes: 58 }),
          episode(2, { name: 'Tonight', airDate: '2026-10-28' }),
          episode(3, { name: 'Next Week', airDate: '2026-11-04' }),
          episode(4, { name: 'Someday', airDate: null }),
        ]),
      ),
  }
}

function rowFor(name: string) {
  return screen.getByRole('heading', { name: new RegExp(name) }).closest('li') as HTMLElement
}

describe('SeasonPage', () => {
  beforeEach(() => vi.useFakeTimers({ toFake: ['Date'] }))
  afterEach(() => vi.useRealTimers())

  it("labels episodes as aired, airing today, upcoming or not announced in the user's time zone", async () => {
    // 2026-10-27 20:00 UTC is already 28 October in Colombo (UTC+5:30).
    vi.setSystemTime(new Date('2026-10-27T20:00:00Z'))
    mockApi(routes({ ...testUser, timeZone: 'Asia/Colombo' }))
    renderRoute('/shows/tmdb:1/seasons/1')

    await screen.findByRole('heading', { level: 1, name: 'Season 1' })
    expect(within(rowFor('Arrival')).queryByText(/upcoming|airs today/i)).not.toBeInTheDocument()
    expect(within(rowFor('Arrival')).getByText('58 min')).toBeInTheDocument()
    expect(within(rowFor('Tonight')).getByText('Airs today')).toBeInTheDocument()
    expect(within(rowFor('Next Week')).getByText('Upcoming')).toBeInTheDocument()
    expect(within(rowFor('Someday')).getByText('Date not announced')).toBeInTheDocument()
    expect(screen.getByText(/based on today in Asia\/Colombo/)).toBeInTheDocument()
  })

  it('uses the same instant differently for a user behind UTC', async () => {
    vi.setSystemTime(new Date('2026-10-27T20:00:00Z'))
    mockApi(routes({ ...testUser, timeZone: 'America/Los_Angeles' }))
    renderRoute('/shows/tmdb:1/seasons/1')

    await screen.findByRole('heading', { level: 1, name: 'Season 1' })
    // Still 27 October in Los Angeles: the 28 October episode has not aired yet.
    expect(within(rowFor('Tonight')).getByText('Upcoming')).toBeInTheDocument()
  })

  it('shows an empty state for an announced season without episodes', async () => {
    mockApi({
      ...routes(),
      'GET /api/v1/catalog/shows/tmdb%3A1/seasons/1': () => json(200, season([])),
    })
    renderRoute('/shows/tmdb:1/seasons/1')

    expect(await screen.findByText('No episodes announced yet')).toBeInTheDocument()
  })

  it('reports a missing season', async () => {
    mockApi({
      ...routes(),
      'GET /api/v1/catalog/shows/tmdb%3A1/seasons/1': () => problem(404, 'season-not-found', 'Not found.'),
    })
    renderRoute('/shows/tmdb:1/seasons/1')

    expect(await screen.findByRole('alert')).toHaveTextContent('Season not found')
  })

  it('rejects a non-numeric season without calling the API', async () => {
    mockApi({ ...signedInRoutes(), 'GET /api/v1/catalog/shows/tmdb%3A1': () => json(200, details()) })
    renderRoute('/shows/tmdb:1/seasons/abc')

    expect(await screen.findByRole('alert')).toHaveTextContent('Season not found')
  })
})
