import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { HistoryItem } from '../features/tracking/types'
import { signedInRoutes } from '../test/catalogFixtures'
import { json, mockApi, problem } from '../test/mockApi'
import { renderRoute } from '../test/renderRoute'

const FIRST = 'GET /api/v1/me/history?size=30'
const SECOND = 'GET /api/v1/me/history?size=30&cursor=next-1'

function item(watchId: number, watchedAt: string, overrides: Partial<HistoryItem> = {}): HistoryItem {
  return {
    watchId,
    watchedAt,
    cycle: 1,
    inLibrary: true,
    show: { ref: 'tmdb:1', name: 'Fixture Harbour', posterUrl: null, firstAirDate: null, status: 'RETURNING' },
    episode: { id: 100 + watchId, season: 1, episode: watchId, name: `Episode ${watchId}`, runtimeMinutes: 45 },
    ...overrides,
  }
}

describe('HistoryPage', () => {
  // The test user is in Asia/Colombo (UTC+5:30); it is 2 November there.
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-02T06:00:00Z'))
  })
  afterEach(() => vi.useRealTimers())

  it("groups viewings by day in the user's time zone and labels rewatches and removed shows", async () => {
    mockApi({
      ...signedInRoutes(),
      [FIRST]: () =>
        json(200, {
          items: [
            // 20:00 UTC on 1 November is already 2 November (01:30) in Colombo.
            item(3, '2026-11-01T20:00:00Z', { cycle: 2 }),
            item(2, '2026-11-01T10:00:00Z', { inLibrary: false }),
            item(1, '2026-10-20T10:00:00Z'),
          ],
          nextCursor: null,
        }),
    })
    renderRoute('/history')

    const today = await screen.findByRole('region', { name: 'Today' })
    const viewing = within(today).getByRole('listitem')
    expect(within(viewing).getByText(/01:30/)).toHaveAttribute('datetime', '2026-11-01T20:00:00Z')
    expect(viewing).toHaveTextContent('Fixture Harbour S1 E3 · Episode 3')
    expect(viewing).toHaveTextContent('Rewatch 1')
    const yesterday = screen.getByRole('region', { name: 'Yesterday' })
    expect(within(yesterday).getByText('Removed from library')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Oct 20, 2026' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument()
  })

  it('loads older pages with the cursor and keeps them if a later page fails', async () => {
    let failSecond = false
    const api = mockApi({
      ...signedInRoutes(),
      [FIRST]: () => json(200, { items: [item(2, '2026-11-01T10:00:00Z')], nextCursor: 'next-1' }),
      [SECOND]: () =>
        failSecond
          ? problem(500, 'internal-error', 'Something went wrong.')
          : json(200, { items: [item(1, '2026-10-20T10:00:00Z')], nextCursor: null }),
    })
    renderRoute('/history')
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })

    failSecond = true
    await user.click(await screen.findByRole('button', { name: 'Load more' }))
    expect(await screen.findByText('Could not load more history')).toBeInTheDocument()
    expect(screen.getByText('S1 E2 · Episode 2')).toBeInTheDocument()

    failSecond = false
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('S1 E1 · Episode 1')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument()
    expect(api.calls(SECOND)).toHaveLength(2)
  })

  it('has an empty state', async () => {
    mockApi({ ...signedInRoutes(), [FIRST]: () => json(200, { items: [], nextCursor: null }) })
    renderRoute('/history')

    expect(await screen.findByText('No viewing history yet')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to your library' })).toHaveAttribute('href', '/library')
  })
})
