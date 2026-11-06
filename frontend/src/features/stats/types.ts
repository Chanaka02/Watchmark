import type { LibraryShow } from '../library/types'

/** Mirrors backend `StatsDtos`. Nothing is estimated: unknown runtimes are counted separately. */
export type StatsRange = 'week' | 'month' | 'all'

export type Stats = {
  range: StatsRange
  /** First local date covered; `null` for `all`. */
  from: string | null
  to: string
  uniqueEpisodes: number
  totalWatches: number
  minutesWatched: number
  watchesWithUnknownRuntime: number
  showsWatched: number
  library: { watching: number; completed: number }
  topShows: { show: LibraryShow; watches: number; minutes: number }[]
  topGenres: { name: string; watches: number; minutes: number }[]
  activity: { unit: 'DAY' | 'MONTH'; points: { period: string; watches: number; minutes: number }[] }
}
