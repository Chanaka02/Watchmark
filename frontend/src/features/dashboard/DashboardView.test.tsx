import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'

import { signedInRoutes } from '../../test/catalogFixtures'
import { json, mockApi, problem, setCsrfCookie } from '../../test/mockApi'
import { renderRoute } from '../../test/renderRoute'
import { counts, libraryEntry } from '../../test/trackingFixtures'
import type { HistoryItem } from '../tracking/types'
import type { Dashboard } from './types'

const show = (ref: string, name: string) => ({ ref, name, posterUrl: null, firstAirDate: null, status: 'RETURNING' as const })

function viewing(watchId: number, watchedAt: string, overrides: Partial<HistoryItem> = {}): HistoryItem {
  return {
    watchId,
    watchedAt,
    cycle: 1,
    inLibrary: true,
    show: show('tmdb:1', 'Fixture Harbour'),
    episode: { id: 100 + watchId, season: 1, episode: watchId, name: `Episode ${watchId}`, runtimeMinutes: 45 },
    ...overrides,
  }
}

const full: Dashboard = {
  today: '2026-11-02',
  continueWatching: [
    {
      show: show('tmdb:1', 'Fixture Harbour'),
      cycle: 1,
      progress: counts(2, 5, 6),
      nextEpisode: { id: 103, season: 1, episode: 3, name: 'Third Light', airDate: '2026-10-14' },
      lastWatchedAt: '2026-11-01T20:00:00Z',
    },
  ],
  upcoming: [
    { show: show('tmdb:1', 'Fixture Harbour'), episode: { id: 106, season: 1, episode: 6, name: 'Tonight', airDate: '2026-11-02' } },
    { show: show('tmdb:2', 'Other Fixture'), episode: { id: 201, season: 2, episode: 1, name: 'Premiere', airDate: '2026-11-03' } },
    { show: show('tmdb:1', 'Fixture Harbour'), episode: { id: 107, season: 1, episode: 7, name: 'Later', airDate: '2026-11-10' } },
  ],
  // 20:00 UTC on 1 November is 01:30 on 2 November in Colombo (the test user's zone): "Today".
  recentlyWatched: [viewing(2, '2026-11-01T20:00:00Z'), viewing(1, '2026-10-31T12:00:00Z')],
  recentlyAdded: [libraryEntry({ show: show('tmdb:2', 'Other Fixture'), status: 'PLAN_TO_WATCH' })],
}

function routes(dashboard: () => Dashboard, extra: Parameters<typeof mockApi>[0] = {}) {
  return { ...signedInRoutes(), 'GET /api/v1/me/dashboard': () => json(200, dashboard()), ...extra }
}

function section(name: string) {
  return screen.getByRole('region', { name })
}

describe('Dashboard', () => {
  beforeEach(() => setCsrfCookie())

  it('invites a new user to discover shows instead of showing placeholders', async () => {
    mockApi(signedInRoutes())
    renderRoute('/')

    expect(await screen.findByText('Your dashboard is empty')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Discover shows' })).toHaveAttribute('href', '/discover')
    expect(screen.queryByRole('region', { name: 'Continue watching' })).not.toBeInTheDocument()
  })

  it('shows what to watch next, upcoming episodes by day, recent viewings and additions', async () => {
    mockApi(routes(() => full))
    renderRoute('/')

    const card = within(await screen.findByRole('region', { name: 'Continue watching' })).getByRole('article', {
      name: 'Fixture Harbour',
    })
    expect(within(card).getByText('S1 E3 · Third Light')).toBeInTheDocument()
    expect(within(card).getByRole('progressbar')).toHaveAttribute('aria-valuetext', '2 of 5 aired episodes watched')

    const upcoming = section('Upcoming')
    expect(within(upcoming).getAllByRole('heading', { level: 3 }).map((h) => h.textContent)).toEqual([
      'Today',
      'Tomorrow',
      'Nov 10, 2026',
    ])
    expect(within(upcoming).getByText('S2 E1 · Premiere')).toBeInTheDocument()

    const recent = section('Recently watched')
    expect(within(recent).getByRole('link', { name: 'Full history' })).toHaveAttribute('href', '/history')
    expect(within(recent).getAllByRole('listitem')[0]).toHaveTextContent(/Today, 01:30/)

    expect(within(section('Recently added')).getByRole('link', { name: /Other Fixture/ })).toHaveAttribute(
      'href',
      '/shows/tmdb:2',
    )
  })

  it('marks the next episode watched from the dashboard and offers undo', async () => {
    let dashboard = full
    const api = mockApi(
      routes(() => dashboard, {
        'PUT /api/v1/tracking/episodes/103/watch': () => {
          dashboard = { ...full, continueWatching: [] }
          return json(200, { operationId: 'op-1', showRef: 'tmdb:1', created: 1, alreadyWatched: 0, notEligible: 0 })
        },
      }),
    )
    renderRoute('/')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Mark S1 E3 watched' }))

    const status = await screen.findByRole('status')
    expect(await within(status).findByText('Marked S1 E3 watched.')).toBeInTheDocument()
    expect(within(status).getByRole('button', { name: 'Undo' })).toBeInTheDocument()
    expect(await screen.findByText("You're caught up on everything you're watching.")).toBeInTheDocument()
    expect(api.calls('PUT /api/v1/tracking/episodes/103/watch')).toHaveLength(1)
  })

  it('reports a failed load with retry', async () => {
    mockApi(routes(() => full, { 'GET /api/v1/me/dashboard': () => problem(500, 'internal-error', 'Something went wrong.') }))
    renderRoute('/')

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Could not load your dashboard')
    expect(within(alert).getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
