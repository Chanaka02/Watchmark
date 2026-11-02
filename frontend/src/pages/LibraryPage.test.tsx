import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { signedInRoutes } from '../test/catalogFixtures'
import { json, mockApi, problem } from '../test/mockApi'
import { renderRoute } from '../test/renderRoute'
import type { LibraryEntry, LibraryPage } from '../features/library/types'

function entry(ref: string, name: string, overrides: Partial<LibraryEntry> = {}): LibraryEntry {
  return {
    show: { ref, name, posterUrl: null, firstAirDate: null, status: 'RETURNING' },
    status: 'WATCHING',
    rating: null,
    notes: null,
    cycle: 1,
    addedAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
    ...overrides,
  }
}

const counts = { WATCHING: 2, PLAN_TO_WATCH: 0, COMPLETED: 1, ON_HOLD: 0, DROPPED: 0 }

function page(entries: LibraryEntry[], pageCounts: LibraryPage['counts'] = counts): LibraryPage {
  return { entries, counts: pageCounts }
}

describe('LibraryPage', () => {
  it('lists entries with status, rating and counts per tab', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/v1/library?sort=RECENT': () =>
        json(200, page([entry('tmdb:1', 'Fixture Harbour', { rating: 9 }), entry('tmdb:2', 'Other Fixture')])),
    })
    renderRoute('/library')

    const card = await screen.findByRole('link', { name: /fixture harbour/i })
    expect(card).toHaveAttribute('href', '/shows/tmdb:1')
    expect(within(card).getByText('Watching')).toBeInTheDocument()
    expect(within(card).getByText('9/10')).toBeInTheDocument()

    const tabs = screen.getByRole('navigation', { name: 'Library status' })
    expect(within(tabs).getByRole('button', { name: 'All 3' })).toHaveAttribute('aria-pressed', 'true')
    expect(within(tabs).getByRole('button', { name: 'Completed 1' })).toBeInTheDocument()
  })

  it('filters by status and changes sort through the URL', async () => {
    const api = mockApi({
      ...signedInRoutes(),
      'GET /api/v1/library?sort=RECENT': () => json(200, page([entry('tmdb:1', 'Fixture Harbour')])),
      'GET /api/v1/library?sort=RECENT&status=COMPLETED': () =>
        json(200, page([entry('tmdb:3', 'Done Fixture', { status: 'COMPLETED' })])),
      'GET /api/v1/library?sort=TITLE&status=COMPLETED': () =>
        json(200, page([entry('tmdb:3', 'Done Fixture', { status: 'COMPLETED' })])),
    })
    const { router } = renderRoute('/library')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^Completed/ }))
    expect(await screen.findByRole('link', { name: /done fixture/i })).toBeInTheDocument()
    expect(router.state.location.search).toBe('?status=COMPLETED')

    await user.selectOptions(screen.getByLabelText('Sort by'), 'Title')
    await vi.waitFor(() => expect(api.calls('GET /api/v1/library?sort=TITLE&status=COMPLETED')).toHaveLength(1))
    api.assertNoUnexpectedRequests()
  })

  it('invites an empty library to discover shows', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/v1/library?sort=RECENT': () =>
        json(200, page([], { WATCHING: 0, PLAN_TO_WATCH: 0, COMPLETED: 0, ON_HOLD: 0, DROPPED: 0 })),
    })
    renderRoute('/library')

    expect(await screen.findByText('Your library is empty')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Discover shows' })).toHaveAttribute('href', '/discover')
  })

  it('distinguishes an empty filter from an empty library', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/v1/library?sort=RECENT&status=DROPPED': () => json(200, page([])),
    })
    renderRoute('/library?status=DROPPED')

    expect(await screen.findByText('No shows marked “Dropped”')).toBeInTheDocument()
  })

  it('shows an error with retry', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/v1/library?sort=RECENT': () => problem(500, 'internal-error', 'Something went wrong.'),
    })
    renderRoute('/library')

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Could not load your library')
    expect(within(alert).getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
