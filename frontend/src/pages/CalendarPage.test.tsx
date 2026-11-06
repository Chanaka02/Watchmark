import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { Calendar, CalendarEpisode } from '../features/calendar/types'
import { signedInRoutes } from '../test/catalogFixtures'
import { json, mockApi, problem } from '../test/mockApi'
import { renderRoute } from '../test/renderRoute'

const LIST = 'GET /api/v1/me/calendar?from=2026-11-02&to=2026-11-29'
const NEXT_LIST = 'GET /api/v1/me/calendar?from=2026-11-30&to=2026-12-27'
const NOVEMBER = 'GET /api/v1/me/calendar?from=2026-10-26&to=2026-12-06'
const DECEMBER = 'GET /api/v1/me/calendar?from=2026-11-30&to=2027-01-03'

const harbour = { ref: 'tmdb:1', name: 'Fixture Harbour', posterUrl: null, firstAirDate: null, status: 'RETURNING' as const }
const other = { ref: 'tmdb:2', name: 'Other Fixture', posterUrl: null, firstAirDate: null, status: 'RETURNING' as const }

function airing(id: number, airDate: string, overrides: Partial<CalendarEpisode['episode']> = {}, watched = false): CalendarEpisode {
  return {
    show: id > 200 ? other : harbour,
    episode: { id, season: 1, episode: id % 100, name: `Episode ${id % 100}`, airDate, runtimeMinutes: 45, ...overrides },
    watched,
  }
}

function calendar(from: string, to: string, overrides: Partial<Calendar> = {}): Calendar {
  return { today: '2026-11-02', from, to, episodes: [], unscheduled: [], ...overrides }
}

const november = calendar('2026-11-02', '2026-11-29', {
  episodes: [
    airing(101, '2026-11-02', {}, true),
    airing(201, '2026-11-02'),
    airing(102, '2026-11-03', { season: 0, episode: 1, name: 'Holiday Special' }),
    airing(103, '2026-11-12'),
  ],
  unscheduled: [
    {
      show: other,
      episodes: Array.from({ length: 7 }, (_, i) => ({ id: 300 + i, season: 2, episode: i + 1, name: `Episode ${i + 1}` })),
    },
  ],
})

function section(name: string) {
  return screen.getByRole('region', { name })
}

describe('CalendarPage', () => {
  // 06:00 UTC is 11:30 on 2 November in Colombo, the test user's zone.
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-02T06:00:00Z'))
  })
  afterEach(() => vi.useRealTimers())

  it('lists four weeks of air dates by day, with watched state and specials', async () => {
    mockApi({ ...signedInRoutes(), [LIST]: () => json(200, november) })
    renderRoute('/calendar')

    const today = await screen.findByRole('region', { name: 'Today' })
    expect(within(today).getAllByRole('listitem')).toHaveLength(2)
    expect(within(today).getAllByRole('listitem')[0]).toHaveTextContent('Fixture Harbour S1 E1 · Episode 1Watched')
    expect(within(section('Tomorrow')).getByText(/Special 1 · Holiday Special/)).toBeInTheDocument()
    expect(section('Nov 12, 2026')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Today' })).toBeDisabled()
  })

  it('lists episodes without an announced date separately, never with a date', async () => {
    mockApi({ ...signedInRoutes(), [LIST]: () => json(200, november) })
    renderRoute('/calendar')

    const unscheduled = await screen.findByRole('region', { name: 'Date not announced' })
    expect(within(unscheduled).getByRole('link', { name: 'Other Fixture' })).toHaveAttribute('href', '/shows/tmdb:2')
    expect(within(unscheduled).getByText('S2 E1, S2 E2, S2 E3, S2 E4, S2 E5 and 2 more')).toBeInTheDocument()
    expect(unscheduled.querySelector('time')).toBeNull()
  })

  it('moves through the list four weeks at a time', async () => {
    const api = mockApi({
      ...signedInRoutes(),
      [LIST]: () => json(200, november),
      [NEXT_LIST]: () => json(200, calendar('2026-11-30', '2026-12-27')),
    })
    const { router } = renderRoute('/calendar')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Next 4 weeks' }))

    expect(await screen.findByText('Nothing airs in these four weeks for the shows you follow.')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?view=list&from=2026-11-30')
    expect(api.calls(NEXT_LIST)).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Today' }))
    expect(await screen.findByRole('region', { name: 'Today' })).toBeInTheDocument()
  })

  it('shows a Monday-first month grid with today marked', async () => {
    const api = mockApi({
      ...signedInRoutes(),
      [NOVEMBER]: () =>
        json(200, { ...november, from: '2026-10-26', to: '2026-12-06', episodes: [...november.episodes, airing(104, '2026-10-26')] }),
      [DECEMBER]: () => json(200, calendar('2026-11-30', '2027-01-03')),
    })
    renderRoute('/calendar?view=month')
    const user = userEvent.setup()

    const grid = await screen.findByRole('table', { name: 'November 2026' })
    expect(within(grid).getAllByRole('columnheader').map((th) => th.textContent)).toEqual([
      'Mon',
      'Tue',
      'Wed',
      'Thu',
      'Fri',
      'Sat',
      'Sun',
    ])
    expect(within(grid).getAllByRole('row')).toHaveLength(7)
    const todayCell = grid.querySelector('[aria-current="date"]')!.closest('td')!
    expect(within(todayCell).getAllByRole('link').map((a) => a.textContent)).toEqual(['Fixture Harbour', 'Other Fixture'])
    expect(within(grid.querySelector('time[datetime="2026-10-26"]')!.closest('td')!).getByText('S1 E4')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'This month' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Next month' }))
    expect(await screen.findByRole('table', { name: 'December 2026' })).toBeInTheDocument()
    expect(api.calls(DECEMBER)).toHaveLength(1)
  })

  it('switches views with pressed toggle buttons', async () => {
    mockApi({ ...signedInRoutes(), [LIST]: () => json(200, november), [NOVEMBER]: () => json(200, november) })
    const { router } = renderRoute('/calendar')
    const user = userEvent.setup()

    const views = await screen.findByRole('group', { name: 'Calendar view' })
    expect(within(views).getByRole('button', { name: 'List' })).toHaveAttribute('aria-pressed', 'true')
    await user.click(within(views).getByRole('button', { name: 'Month' }))

    expect(await screen.findByRole('table', { name: 'November 2026' })).toBeInTheDocument()
    expect(within(views).getByRole('button', { name: 'Month' })).toHaveAttribute('aria-pressed', 'true')
    expect(router.state.location.search).toBe('?view=month')
  })

  it('reports a failed load with retry', async () => {
    mockApi({ ...signedInRoutes(), [LIST]: () => problem(500, 'internal-error', 'Something went wrong.') })
    renderRoute('/calendar')

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Could not load the calendar')
    expect(within(alert).getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
