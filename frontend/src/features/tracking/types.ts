import type { LibraryShow, LibraryStatus } from '../library/types'

/** Mirrors backend `TrackingDtos`. */
export type ProgressState = 'NOT_STARTED' | 'IN_PROGRESS' | 'CAUGHT_UP' | 'COMPLETE'

/** `eligible` = aired (or already watched) episodes; the denominator of `percent`. */
export type ProgressCounts = {
  watched: number
  eligible: number
  total: number
  percent: number
  state: ProgressState
}

export type SeasonSummary = ProgressCounts & { number: number; special: boolean }

export type NextEpisode = { id: number; season: number; episode: number; name: string; airDate: string | null }

export type ShowProgress = {
  showRef: string
  cycle: number
  status: LibraryStatus
  overall: ProgressCounts
  history: { uniqueEpisodes: number; totalWatches: number }
  seasons: SeasonSummary[]
  nextEpisode: NextEpisode | null
  canRewatch: boolean
}

export type EpisodeProgress = {
  id: number
  externalId: string | null
  episode: number
  name: string
  airDate: string | null
  eligible: boolean
  watched: boolean
  watchedAt: string | null
  upstreamMissing: boolean
}

export type SeasonProgress = ProgressCounts & {
  showRef: string
  cycle: number
  number: number
  special: boolean
  episodes: EpisodeProgress[]
}

export type OperationType = 'WATCH' | 'UNWATCH' | 'SEASON' | 'UP_TO'

export type WatchResult = {
  operationId: string | null
  showRef: string
  created: number
  alreadyWatched: number
  notEligible: number
}

export type UnwatchResult = { operationId: string | null; showRef: string; removed: number }

export type UndoResult = { operationId: string; type: OperationType; showRef: string; reverted: number }

/** One viewing; `inLibrary` is false once the show was removed (its history is kept). */
export type HistoryItem = {
  watchId: number
  watchedAt: string
  cycle: number
  inLibrary: boolean
  show: LibraryShow
  episode: { id: number; season: number; episode: number; name: string; runtimeMinutes: number | null }
}

export type HistoryPage = { items: HistoryItem[]; nextCursor: string | null }
