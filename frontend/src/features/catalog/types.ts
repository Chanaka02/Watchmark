/**
 * Mirrors backend `CatalogDtos`. Fields typed `| null` really can be null:
 * providers omit data and the backend never invents values.
 * Dates are calendar dates (`YYYY-MM-DD`) with no time zone.
 */

export type CatalogSource = { provider: string; mock: boolean }

export type ResultPage<T> = { items: T[]; page: number; totalPages: number; totalResults: number }

export type ShowSummary = {
  ref: string
  name: string
  firstAirDate: string | null
  overview: string | null
  posterUrl: string | null
  voteAverage: number | null
}

export type ShowStatus = 'RETURNING' | 'IN_PRODUCTION' | 'PLANNED' | 'PILOT' | 'ENDED' | 'CANCELED' | 'UNKNOWN'

export type SeasonSummary = {
  seasonNumber: number
  name: string
  episodeCount: number | null
  airDate: string | null
  posterUrl: string | null
}

export type EpisodeInfo = {
  externalId: string
  seasonNumber: number
  episodeNumber: number
  name: string
  overview: string | null
  airDate: string | null
  runtimeMinutes: number | null
  stillUrl: string | null
}

export type CastMember = { name: string; character: string | null; profileUrl: string | null }

export type ShowDetails = {
  ref: string
  name: string
  originalName: string | null
  overview: string | null
  tagline: string | null
  status: ShowStatus
  firstAirDate: string | null
  lastAirDate: string | null
  posterUrl: string | null
  backdropUrl: string | null
  genres: string[]
  networks: string[]
  episodeRunTimeMinutes: number | null
  numberOfSeasons: number | null
  numberOfEpisodes: number | null
  voteAverage: number | null
  seasons: SeasonSummary[]
  nextEpisode: EpisodeInfo | null
  lastEpisode: EpisodeInfo | null
  cast: CastMember[]
}

export type SeasonDetails = {
  showRef: string
  seasonNumber: number
  name: string
  overview: string | null
  airDate: string | null
  posterUrl: string | null
  episodes: EpisodeInfo[]
}

export type DiscoverListSlug = 'trending' | 'popular' | 'top-rated' | 'on-the-air'

export const discoverLists: { slug: DiscoverListSlug; label: string }[] = [
  { slug: 'trending', label: 'Trending' },
  { slug: 'popular', label: 'Popular' },
  { slug: 'top-rated', label: 'Top rated' },
  { slug: 'on-the-air', label: 'On the air' },
]

export const statusLabels: Record<ShowStatus, string> = {
  RETURNING: 'Returning series',
  IN_PRODUCTION: 'In production',
  PLANNED: 'Planned',
  PILOT: 'Pilot',
  ENDED: 'Ended',
  CANCELED: 'Canceled',
  UNKNOWN: 'Status unknown',
}
