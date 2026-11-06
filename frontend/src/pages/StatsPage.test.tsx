import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import type { Stats } from '../features/stats/types'
import { formatMinutes } from '../features/stats/useStats'
import { signedInRoutes } from '../test/catalogFixtures'
import { json, mockApi, problem } from '../test/mockApi'
import { renderRoute } from '../test/renderRoute'

const MONTH = 'GET /api/v1/me/stats?range=month'
const ALL = 'GET /api/v1/me/stats?range=all'

const show = (ref: string, name: string) => ({ ref, name, posterUrl: null, firstAirDate: null, status: 'RETURNING' as const })

function stats(overrides: Partial<Stats> = {}): Stats {
  return {
    range: 'month',
    from: '2026-10-04',
    to: '2026-11-02',
    uniqueEpisodes: 5,
    totalWatches: 6,
    minutesWatched: 215,
    watchesWithUnknownRuntime: 1,
    showsWatched: 3,
    library: { watching: 2, completed: 1 },
    topShows: [
      { show: show('tmdb:1', 'Fixture Harbour'), watches: 4, minutes: 150 },
      { show: show('tmdb:2', 'Other Fixture'), watches: 2, minutes: 65 },
    ],
    topGenres: [{ name: 'Drama', watches: 4, minutes: 150 }],
    activity: {
      unit: 'DAY',
      points: [
        { period: '2026-10-30', watches: 0, minutes: 0 },
        { period: '2026-10-31', watches: 3, minutes: 100 },
        { period: '2026-11-01', watches: 1, minutes: 50 },
      ],
    },
    ...overrides,
  }
}

function tile(label: string) {
  return screen.getByText(label).closest('div') as HTMLElement
}

describe('StatsPage', () => {
  it('shows unique episodes, viewings, time with unknown runtimes called out, and library counts', async () => {
    mockApi({ ...signedInRoutes(), [MONTH]: () => json(200, stats()) })
    renderRoute('/stats')

    expect(await screen.findByText('Episodes watched')).toBeInTheDocument()
    expect(tile('Episodes watched')).toHaveTextContent('56 viewings including rewatches')
    expect(tile('Time watched')).toHaveTextContent('3 h 35 min1 viewing with unknown runtime not included')
    expect(tile('In your library')).toHaveTextContent('2 watching1 completed')
    expect(screen.getByText('Oct 4, 2026 – Nov 2, 2026')).toBeInTheDocument()
  })

  it('charts activity with a summary and a table of the same numbers', async () => {
    mockApi({ ...signedInRoutes(), [MONTH]: () => json(200, stats()) })
    renderRoute('/stats')

    const chart = await screen.findByRole('img', { name: /^4 viewings; most on Oct 31, 2026 \(3\)/ })
    expect(chart).toBeInTheDocument()
    const table = screen.getByRole('table', { name: 'Viewings per day' })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows.map((row) => row.textContent)).toEqual([
      'Oct 30, 202600 min',
      'Oct 31, 202631 h 40 min',
      'Nov 1, 2026150 min',
    ])
  })

  it('ranks shows and genres', async () => {
    mockApi({ ...signedInRoutes(), [MONTH]: () => json(200, stats()) })
    renderRoute('/stats')

    const shows = await screen.findByRole('region', { name: 'Most watched shows' })
    expect(within(shows).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      'Fixture Harbour4 viewings · 2 h 30 min',
      'Other Fixture2 viewings · 1 h 5 min',
    ])
    expect(within(shows).getByRole('link', { name: 'Fixture Harbour' })).toHaveAttribute('href', '/shows/tmdb:1')
    expect(screen.getByRole('region', { name: 'Top genres' })).toHaveTextContent('A viewing counts for every genre')
  })

  it('switches the period through the URL', async () => {
    const api = mockApi({
      ...signedInRoutes(),
      [MONTH]: () => json(200, stats()),
      [ALL]: () =>
        json(
          200,
          stats({
            range: 'all',
            from: null,
            activity: { unit: 'MONTH', points: [{ period: '2026-10', watches: 6, minutes: 215 }] },
          }),
        ),
    })
    const { router } = renderRoute('/stats')
    const user = userEvent.setup()

    const period = await screen.findByRole('group', { name: 'Period' })
    expect(within(period).getByRole('button', { name: 'Last 30 days' })).toHaveAttribute('aria-pressed', 'true')
    await user.click(within(period).getByRole('button', { name: 'All time' }))

    expect(await screen.findByRole('heading', { name: 'Viewings per month' })).toBeInTheDocument()
    expect(screen.getByText('Up to Nov 2, 2026')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?range=all')
    expect(api.calls(ALL)).toHaveLength(1)
  })

  it('shows zeros and an explanation rather than placeholder data', async () => {
    mockApi({
      ...signedInRoutes(),
      [MONTH]: () =>
        json(
          200,
          stats({
            uniqueEpisodes: 0,
            totalWatches: 0,
            minutesWatched: 0,
            watchesWithUnknownRuntime: 0,
            showsWatched: 0,
            library: { watching: 0, completed: 0 },
            topShows: [],
            topGenres: [],
          }),
        ),
    })
    renderRoute('/stats')

    expect(await screen.findByText('No viewings in this period')).toBeInTheDocument()
    expect(tile('Time watched')).toHaveTextContent('0 min')
    expect(screen.queryByRole('img', { name: /viewings/ })).not.toBeInTheDocument()
  })

  it('reports a failed load with retry', async () => {
    mockApi({ ...signedInRoutes(), [MONTH]: () => problem(500, 'internal-error', 'Something went wrong.') })
    renderRoute('/stats')

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Could not load your statistics')
    expect(within(alert).getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})

describe('formatMinutes', () => {
  it('formats hours and minutes', () => {
    expect(formatMinutes(0)).toBe('0 min')
    expect(formatMinutes(45)).toBe('45 min')
    expect(formatMinutes(120)).toBe('2 h')
    expect(formatMinutes(615)).toBe('10 h 15 min')
  })
})
