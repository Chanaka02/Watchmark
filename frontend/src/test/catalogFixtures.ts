import type { EpisodeInfo, ResultPage, SeasonDetails, ShowDetails, ShowSummary } from '../features/catalog/types'
import { json, testUser } from './mockApi'

/** Test data only: invented shows. */
export function summary(id: number, overrides: Partial<ShowSummary> = {}): ShowSummary {
  return {
    ref: `tmdb:${id}`,
    name: `Show ${id}`,
    firstAirDate: '2021-03-14',
    overview: null,
    posterUrl: null,
    voteAverage: null,
    ...overrides,
  }
}

export function page(items: ShowSummary[], pageNumber = 1, totalPages = 1, totalResults = items.length): ResultPage<ShowSummary> {
  return { items, page: pageNumber, totalPages, totalResults }
}

export function episode(number: number, overrides: Partial<EpisodeInfo> = {}): EpisodeInfo {
  return {
    externalId: `ep-${number}`,
    seasonNumber: 1,
    episodeNumber: number,
    name: `Episode ${number}`,
    overview: null,
    airDate: null,
    runtimeMinutes: null,
    stillUrl: null,
    ...overrides,
  }
}

export function details(overrides: Partial<ShowDetails> = {}): ShowDetails {
  return {
    ref: 'tmdb:1',
    name: 'Fixture Harbour',
    originalName: 'Fixture Harbour',
    overview: 'An invented drama.',
    tagline: null,
    status: 'RETURNING',
    firstAirDate: '2021-03-14',
    lastAirDate: '2026-10-20',
    posterUrl: null,
    backdropUrl: null,
    genres: ['Drama'],
    networks: [],
    episodeRunTimeMinutes: null,
    numberOfSeasons: 1,
    numberOfEpisodes: 6,
    voteAverage: null,
    seasons: [],
    nextEpisode: null,
    lastEpisode: null,
    cast: [],
    ...overrides,
  }
}

export function season(episodes: EpisodeInfo[], overrides: Partial<SeasonDetails> = {}): SeasonDetails {
  return {
    showRef: 'tmdb:1',
    seasonNumber: 1,
    name: 'Season 1',
    overview: null,
    airDate: null,
    posterUrl: null,
    episodes,
    ...overrides,
  }
}

/** Routes every signed-in catalog page needs. */
export function signedInRoutes(source: { provider: string; mock: boolean } = { provider: 'tmdb', mock: false }) {
  return {
    'GET /api/v1/auth/me': () => json(200, testUser),
    'GET /api/v1/catalog/source': () => json(200, source),
  }
}
