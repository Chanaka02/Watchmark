import type { LibraryEntry } from '../features/library/types'
import type { EpisodeProgress, ProgressCounts, SeasonProgress, ShowProgress } from '../features/tracking/types'

/** Test data only, matching `catalogFixtures`: show tmdb:1, catalogue episode ids `ep-<n>`. */
export function libraryEntry(overrides: Partial<LibraryEntry> = {}): LibraryEntry {
  return {
    show: { ref: 'tmdb:1', name: 'Fixture Harbour', posterUrl: null, firstAirDate: null, status: 'RETURNING' },
    status: 'WATCHING',
    rating: null,
    notes: null,
    cycle: 1,
    addedAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
    ...overrides,
  }
}

export function counts(watched: number, eligible: number, total: number): ProgressCounts {
  const percent = eligible === 0 ? 0 : Math.round((watched * 1000) / eligible) / 10
  const state =
    watched === 0 ? 'NOT_STARTED' : watched === total ? 'COMPLETE' : watched === eligible ? 'CAUGHT_UP' : 'IN_PROGRESS'
  return { watched, eligible, total, percent, state }
}

export function showProgress(overrides: Partial<ShowProgress> = {}): ShowProgress {
  return {
    showRef: 'tmdb:1',
    cycle: 1,
    status: 'WATCHING',
    overall: counts(2, 5, 6),
    history: { uniqueEpisodes: 2, totalWatches: 2 },
    seasons: [{ number: 1, special: false, ...counts(2, 5, 6) }],
    nextEpisode: { id: 103, season: 1, episode: 3, name: 'Third Light', airDate: '2026-10-14' },
    canRewatch: true,
    ...overrides,
  }
}

/** Stored episode `100 + n` of season 1. */
export function trackedEpisode(n: number, overrides: Partial<EpisodeProgress> = {}): EpisodeProgress {
  return {
    id: 100 + n,
    externalId: `ep-${n}`,
    episode: n,
    name: `Episode ${n}`,
    airDate: null,
    eligible: true,
    watched: false,
    watchedAt: null,
    upstreamMissing: false,
    ...overrides,
  }
}

export function seasonProgress(episodes: EpisodeProgress[], overrides: Partial<SeasonProgress> = {}): SeasonProgress {
  const watched = episodes.filter((e) => e.watched && !e.upstreamMissing).length
  const eligible = episodes.filter((e) => (e.eligible || e.watched) && !e.upstreamMissing).length
  const total = episodes.filter((e) => !e.upstreamMissing).length
  return { showRef: 'tmdb:1', cycle: 1, number: 1, special: false, ...counts(watched, eligible, total), episodes, ...overrides }
}
