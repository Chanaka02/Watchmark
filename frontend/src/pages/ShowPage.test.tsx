import { screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { details, episode, signedInRoutes } from '../test/catalogFixtures'
import { json, mockApi, problem } from '../test/mockApi'
import { renderRoute } from '../test/renderRoute'

describe('ShowPage', () => {
  beforeEach(() => {
    // Only fake Date, so promises and timers used by React Query keep working.
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-28T06:00:00Z'))
  })
  afterEach(() => vi.useRealTimers())

  it('renders details, putting specials after regular seasons', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/v1/catalog/shows/tmdb%3A1': () =>
        json(
          200,
          details({
            status: 'ENDED',
            firstAirDate: '2019-01-01',
            lastAirDate: '2022-05-01',
            voteAverage: 7.94,
            episodeRunTimeMinutes: 45,
            networks: ['Fixture Broadcasting'],
            genres: ['Drama', 'Mystery'],
            seasons: [
              { seasonNumber: 0, name: 'Specials', episodeCount: 2, airDate: null, posterUrl: null },
              { seasonNumber: 1, name: 'Season 1', episodeCount: 8, airDate: '2019-01-01', posterUrl: null },
              { seasonNumber: 2, name: 'Season 2', episodeCount: null, airDate: null, posterUrl: null },
            ],
            cast: [{ name: 'Lead Performer', character: 'Captain Mara Holt', profileUrl: null }],
          }),
        ),
    })
    renderRoute('/shows/tmdb:1')

    expect(await screen.findByRole('heading', { level: 1, name: 'Fixture Harbour' })).toBeInTheDocument()
    expect(screen.getByText('2019–2022')).toBeInTheDocument()
    expect(screen.getByText('Ended')).toBeInTheDocument()
    expect(screen.getByText('45 min')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Fixture Harbour poster' })).toBeInTheDocument()

    const seasons = within(screen.getByRole('region', { name: 'Seasons' })).getAllByRole('link')
    expect(seasons.map((link) => link.textContent)).toEqual([
      'Season 18 episodes · 2019',
      'Season 2Details not announced',
      'Specials2 episodes',
    ])
    expect(seasons[2]).toHaveAttribute('href', '/shows/tmdb:1/seasons/0')
    expect(screen.getByText('Captain Mara Holt')).toBeInTheDocument()
  })

  it('shows the next episode with its release status', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/v1/catalog/shows/tmdb%3A1': () =>
        json(200, details({ nextEpisode: episode(5, { seasonNumber: 2, airDate: '2026-11-04' }) })),
    })
    renderRoute('/shows/tmdb:1')

    expect(await screen.findByText('Next: S2 E5')).toBeInTheDocument()
    expect(screen.getByText('Upcoming')).toBeInTheDocument()
  })

  it('handles a show with almost no metadata', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/v1/catalog/shows/tmdb%3A1': () =>
        json(200, details({ overview: null, firstAirDate: null, genres: [], seasons: [], status: 'PLANNED' })),
    })
    renderRoute('/shows/tmdb:1')

    expect(await screen.findByText('No synopsis available.')).toBeInTheDocument()
    expect(screen.getByText('No seasons have been announced yet.')).toBeInTheDocument()
  })

  it('reports a missing show', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/v1/catalog/shows/tmdb%3A404': () => problem(404, 'show-not-found', 'This show could not be found.'),
    })
    renderRoute('/shows/tmdb:404')

    expect(await screen.findByRole('alert')).toHaveTextContent('Show not found')
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })

  it('offers a retry when the provider is unavailable', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/v1/catalog/shows/tmdb%3A1': () => problem(503, 'metadata-unavailable', 'Temporarily unavailable.'),
    })
    renderRoute('/shows/tmdb:1')

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Could not load this show')
    expect(within(alert).getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
