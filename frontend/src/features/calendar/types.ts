import type { LibraryShow } from '../library/types'

/** Mirrors backend `CalendarDtos`. Undated episodes have no `airDate` at all. */
export type CalendarEpisode = {
  show: LibraryShow
  episode: { id: number; season: number; episode: number; name: string; airDate: string; runtimeMinutes: number | null }
  watched: boolean
}

export type UnscheduledShow = {
  show: LibraryShow
  episodes: { id: number; season: number; episode: number; name: string }[]
}

export type Calendar = {
  today: string
  from: string
  to: string
  episodes: CalendarEpisode[]
  unscheduled: UnscheduledShow[]
}
