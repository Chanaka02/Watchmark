import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { details, episode, notInLibrary, season, signedInRoutes } from '../../test/catalogFixtures'
import { json, mockApi, problem, setCsrfCookie } from '../../test/mockApi'
import { renderRoute } from '../../test/renderRoute'
import { libraryEntry, seasonProgress, trackedEpisode } from '../../test/trackingFixtures'
import type { SeasonProgress } from './types'

const SEASON_PROGRESS = 'GET /api/v1/library/tmdb%3A1/progress/seasons/1'

const catalogue = season([
  episode(1, { name: 'First Light', airDate: '2026-10-01' }),
  episode(2, { name: 'Second Wind', airDate: '2026-10-08' }),
  episode(3, { name: 'Third Light', airDate: '2026-10-15' }),
  episode(4, { name: 'Far Off', airDate: '2027-01-01' }),
])

function routes(progress: () => SeasonProgress, extra: Parameters<typeof mockApi>[0] = {}) {
  return {
    ...signedInRoutes(),
    'GET /api/v1/catalog/shows/tmdb%3A1': () => json(200, details()),
    'GET /api/v1/catalog/shows/tmdb%3A1/seasons/1': () => json(200, catalogue),
    'GET /api/v1/library/tmdb%3A1': () => json(200, libraryEntry()),
    [SEASON_PROGRESS]: () => json(200, progress()),
    ...extra,
  }
}

const initial = () =>
  seasonProgress([
    trackedEpisode(1, { watched: true, watchedAt: '2026-10-02T20:00:00Z' }),
    trackedEpisode(2),
    trackedEpisode(3),
    trackedEpisode(4, { eligible: false }),
  ])

function row(name: string) {
  return screen.getByRole('heading', { name: new RegExp(name) }).closest('li') as HTMLElement
}

describe('Season tracking', () => {
  beforeEach(() => setCsrfCookie())

  it('asks to add the show before tracking, without requesting progress', async () => {
    const api = mockApi({ ...routes(initial), 'GET /api/v1/library/tmdb%3A1': notInLibrary })
    renderRoute('/shows/tmdb:1/seasons/1')

    expect(await screen.findByRole('link', { name: 'Add this show to your library' })).toHaveAttribute(
      'href',
      '/shows/tmdb:1',
    )
    expect(screen.queryByRole('button', { name: /watched/i })).not.toBeInTheDocument()
    expect(api.calls(SEASON_PROGRESS)).toHaveLength(0)
  })

  it('shows watched state and offers no controls for unaired episodes', async () => {
    mockApi(routes(initial))
    renderRoute('/shows/tmdb:1/seasons/1')

    expect(await screen.findByText('1 of 3 aired episodes watched')).toBeInTheDocument()
    expect(within(row('First Light')).getByRole('button', { name: 'S1 E1 Watched' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(within(row('Second Wind')).getByRole('button', { name: 'S1 E2 Watched' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
    expect(within(row('Far Off')).queryByRole('button')).not.toBeInTheDocument()
  })

  it('marks an episode watched and can undo it', async () => {
    let progress = initial()
    const api = mockApi(
      routes(() => progress, {
        'PUT /api/v1/tracking/episodes/102/watch': () => {
          progress = seasonProgress([
            trackedEpisode(1, { watched: true }),
            trackedEpisode(2, { watched: true }),
            trackedEpisode(3),
            trackedEpisode(4, { eligible: false }),
          ])
          return json(200, { operationId: 'op-1', showRef: 'tmdb:1', created: 1, alreadyWatched: 0, notEligible: 0 })
        },
        'POST /api/v1/tracking/operations/op-1/undo': () => {
          progress = initial()
          return json(200, { operationId: 'op-1', type: 'WATCH', showRef: 'tmdb:1', reverted: 1 })
        },
      }),
    )
    renderRoute('/shows/tmdb:1/seasons/1')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'S1 E2 Watched' }))

    const status = screen.getByRole('status')
    expect(await within(status).findByText('Marked S1 E2 watched.')).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(screen.getByRole('button', { name: 'S1 E2 Watched' })).toHaveAttribute('aria-pressed', 'true'),
    )

    await user.click(within(status).getByRole('button', { name: 'Undo' }))

    expect(await within(status).findByText('Undone.')).toBeInTheDocument()
    expect(within(status).queryByRole('button', { name: 'Undo' })).not.toBeInTheDocument()
    await vi.waitFor(() =>
      expect(screen.getByRole('button', { name: 'S1 E2 Watched' })).toHaveAttribute('aria-pressed', 'false'),
    )
    expect(api.calls('POST /api/v1/tracking/operations/op-1/undo')).toHaveLength(1)
  })

  it('unwatches a watched episode', async () => {
    const api = mockApi(
      routes(initial, {
        'DELETE /api/v1/tracking/episodes/101/watch': () =>
          json(200, { operationId: 'op-2', showRef: 'tmdb:1', removed: 1 }),
      }),
    )
    renderRoute('/shows/tmdb:1/seasons/1')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'S1 E1 Watched' }))

    expect(await screen.findByText('Marked S1 E1 unwatched.')).toBeInTheDocument()
    expect(api.calls('DELETE /api/v1/tracking/episodes/101/watch')).toHaveLength(1)
  })

  it('marks everything up to an episode', async () => {
    const api = mockApi(
      routes(initial, {
        'POST /api/v1/tracking/episodes/103/watch-up-to': () =>
          json(200, { operationId: 'op-3', showRef: 'tmdb:1', created: 2, alreadyWatched: 1, notEligible: 0 }),
      }),
    )
    renderRoute('/shows/tmdb:1/seasons/1')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Up to here: mark everything up to S1 E3 watched' }))

    expect(await screen.findByText('Marked 2 episodes watched. Skipped 1 already watched.')).toBeInTheDocument()
    expect(api.calls('POST /api/v1/tracking/episodes/103/watch-up-to')).toHaveLength(1)
  })

  it('marks the season and disables the button once nothing is left', async () => {
    let progress = initial()
    mockApi(
      routes(() => progress, {
        'POST /api/v1/tracking/shows/tmdb%3A1/seasons/1/watch': () => {
          progress = seasonProgress([
            trackedEpisode(1, { watched: true }),
            trackedEpisode(2, { watched: true }),
            trackedEpisode(3, { watched: true }),
            trackedEpisode(4, { eligible: false }),
          ])
          return json(200, { operationId: 'op-4', showRef: 'tmdb:1', created: 2, alreadyWatched: 1, notEligible: 1 })
        },
      }),
    )
    renderRoute('/shows/tmdb:1/seasons/1')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Mark season watched' }))

    expect(
      await screen.findByText('Marked 2 episodes watched. Skipped 1 already watched, 1 not aired yet.'),
    ).toBeInTheDocument()
    expect(await screen.findByText('Caught up')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Mark season watched' })).toBeDisabled()
  })

  it('explains why an undo failed', async () => {
    mockApi(
      routes(initial, {
        'PUT /api/v1/tracking/episodes/102/watch': () =>
          json(200, { operationId: 'op-5', showRef: 'tmdb:1', created: 1, alreadyWatched: 0, notEligible: 0 }),
        'POST /api/v1/tracking/operations/op-5/undo': () =>
          problem(409, 'undo-window-expired', 'Tracking actions can be undone for 24 hours.'),
      }),
    )
    renderRoute('/shows/tmdb:1/seasons/1')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'S1 E2 Watched' }))
    await user.click(await within(screen.getByRole('status')).findByRole('button', { name: 'Undo' }))

    expect(await screen.findByText('Tracking actions can be undone for 24 hours.')).toBeInTheDocument()
  })

  it('reports a rejected watch', async () => {
    mockApi(
      routes(initial, {
        'PUT /api/v1/tracking/episodes/102/watch': () =>
          problem(422, 'episode-not-aired', 'This episode has not aired yet in your time zone.'),
      }),
    )
    renderRoute('/shows/tmdb:1/seasons/1')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'S1 E2 Watched' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('This episode has not aired yet in your time zone.')
  })

  it('keeps watched episodes the data source no longer lists', async () => {
    mockApi(
      routes(() =>
        seasonProgress([
          trackedEpisode(1, { watched: true }),
          trackedEpisode(9, { externalId: 'gone', name: 'Lost Reel', watched: true, upstreamMissing: true }),
        ]),
      ),
    )
    renderRoute('/shows/tmdb:1/seasons/1')

    const section = await screen.findByRole('region', { name: 'Watched, but no longer listed by the data source' })
    expect(within(section).getByText('Episode 9: Lost Reel')).toBeInTheDocument()
    expect(within(section).getByRole('button', { name: 'S1 E9 Watched' })).toHaveAttribute('aria-pressed', 'true')
  })
})
