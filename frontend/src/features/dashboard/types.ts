import type { LibraryEntry, LibraryShow } from '../library/types'
import type { HistoryItem, NextEpisode, ProgressCounts } from '../tracking/types'

/** Mirrors backend `DashboardDtos`. Lists are empty, never padded, when there is no data. */
export type ContinueWatching = {
  show: LibraryShow
  cycle: number
  progress: ProgressCounts
  nextEpisode: NextEpisode
  lastWatchedAt: string | null
}

export type Upcoming = {
  show: LibraryShow
  episode: { id: number; season: number; episode: number; name: string; airDate: string }
}

export type Dashboard = {
  today: string
  continueWatching: ContinueWatching[]
  upcoming: Upcoming[]
  recentlyWatched: HistoryItem[]
  recentlyAdded: LibraryEntry[]
}
