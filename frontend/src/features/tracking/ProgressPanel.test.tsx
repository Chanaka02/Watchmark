import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'

import { details, notInLibrary, signedInRoutes } from '../../test/catalogFixtures'
import { json, mockApi, setCsrfCookie } from '../../test/mockApi'
import { renderRoute } from '../../test/renderRoute'
import { counts, libraryEntry, showProgress } from '../../test/trackingFixtures'
import type { ShowProgress } from './types'

const PROGRESS = 'GET /api/v1/library/tmdb%3A1/progress'

const show = details({
  seasons: [
    { seasonNumber: 1, name: 'Season 1', airDate: '2026-09-30', episodeCount: 6, posterUrl: null },
    { seasonNumber: 2, name: 'Season 2', airDate: null, episodeCount: null, posterUrl: null },
  ],
})

function routes(progress: () => ShowProgress, extra: Parameters<typeof mockApi>[0] = {}) {
  return {
    ...signedInRoutes(),
    'GET /api/v1/catalog/shows/tmdb%3A1': () => json(200, show),
    'GET /api/v1/library/tmdb%3A1': () => json(200, libraryEntry()),
    [PROGRESS]: () => json(200, progress()),
    ...extra,
  }
}

describe('ProgressPanel', () => {
  beforeEach(() => setCsrfCookie())

  it('is not shown, and progress is not requested, for a show outside the library', async () => {
    const api = mockApi({ ...routes(showProgress), 'GET /api/v1/library/tmdb%3A1': notInLibrary })
    renderRoute('/shows/tmdb:1')

    expect(await screen.findByRole('button', { name: 'Add to library' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Your progress' })).not.toBeInTheDocument()
    expect(api.calls(PROGRESS)).toHaveLength(0)
  })

  it('shows progress, the next episode and progress per season', async () => {
    mockApi(routes(showProgress))
    renderRoute('/shows/tmdb:1')

    const panel = (await screen.findByRole('heading', { name: 'Your progress' })).closest('section') as HTMLElement
    const bar = within(panel).getByRole('progressbar', { name: 'Episodes watched' })
    expect(bar).toHaveAttribute('aria-valuenow', '40')
    expect(bar).toHaveAttribute('aria-valuetext', '2 of 5 aired episodes watched')
    expect(within(panel).getByText('S1 E3 · Third Light')).toBeInTheDocument()
    expect(within(panel).getByText('· 1 not aired yet')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Season 1.*2 of 6 watched/ })).toBeInTheDocument()
  })

  it('marks the next episode watched', async () => {
    let progress = showProgress()
    const api = mockApi(
      routes(() => progress, {
        'PUT /api/v1/tracking/episodes/103/watch': () => {
          progress = showProgress({
            overall: counts(3, 5, 6),
            nextEpisode: { id: 104, season: 1, episode: 4, name: 'Fourth Wall', airDate: '2026-10-21' },
          })
          return json(200, { operationId: 'op-1', showRef: 'tmdb:1', created: 1, alreadyWatched: 0, notEligible: 0 })
        },
      }),
    )
    renderRoute('/shows/tmdb:1')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Mark watched' }))

    expect(await screen.findByText('Marked S1 E3 watched.')).toBeInTheDocument()
    expect(await screen.findByText('S1 E4 · Fourth Wall')).toBeInTheDocument()
    expect(api.calls('PUT /api/v1/tracking/episodes/103/watch')).toHaveLength(1)
  })

  it('says when the user is caught up', async () => {
    mockApi(routes(() => showProgress({ overall: counts(5, 5, 6), nextEpisode: null })))
    renderRoute('/shows/tmdb:1')

    expect(await screen.findByText("You're caught up. More episodes are on the way.")).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Start a rewatch' })).not.toBeInTheDocument()
  })

  it('starts a rewatch after confirmation', async () => {
    let progress = showProgress({
      overall: counts(6, 6, 6),
      history: { uniqueEpisodes: 6, totalWatches: 6 },
      nextEpisode: null,
    })
    const api = mockApi(
      routes(() => progress, {
        'POST /api/v1/library/tmdb%3A1/rewatch': () => {
          progress = showProgress({
            cycle: 2,
            overall: counts(0, 6, 6),
            history: { uniqueEpisodes: 6, totalWatches: 6 },
            canRewatch: false,
          })
          return json(200, libraryEntry({ cycle: 2 }))
        },
      }),
    )
    renderRoute('/shows/tmdb:1')
    const user = userEvent.setup()

    expect(await screen.findByText('You have watched every episode.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Start a rewatch' }))
    const dialog = screen.getByRole('dialog', { name: 'Rewatch Fixture Harbour?' })
    expect(dialog).toHaveTextContent('Everything you watched before stays in your history')
    await user.click(within(dialog).getByRole('button', { name: 'Start rewatch' }))

    expect(await screen.findByText('Rewatch 1')).toBeInTheDocument()
    expect(screen.getByText('Rewatch started.')).toBeInTheDocument()
    expect(api.calls('POST /api/v1/library/tmdb%3A1/rewatch')).toHaveLength(1)
  })

  it('mentions rewatches in the history line', async () => {
    mockApi(routes(() => showProgress({ cycle: 2, history: { uniqueEpisodes: 6, totalWatches: 8 } })))
    renderRoute('/shows/tmdb:1')

    expect(await screen.findByText('8 viewings of 6 episodes across all watch-throughs.')).toBeInTheDocument()
  })
})
