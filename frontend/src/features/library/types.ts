import type { ShowStatus } from '../catalog/types'

/** Mirrors backend `LibraryDtos`. */
export type LibraryStatus = 'PLAN_TO_WATCH' | 'WATCHING' | 'COMPLETED' | 'ON_HOLD' | 'DROPPED'

export type LibraryShow = {
  ref: string
  name: string
  posterUrl: string | null
  firstAirDate: string | null
  status: ShowStatus
}

export type LibraryEntry = {
  show: LibraryShow
  status: LibraryStatus
  rating: number | null
  notes: string | null
  cycle: number
  addedAt: string
  updatedAt: string
}

export type LibrarySort = 'RECENT' | 'TITLE' | 'ADDED'

export type LibraryPage = { entries: LibraryEntry[]; counts: Record<LibraryStatus, number> }

export const MAX_NOTES = 2000

export const libraryStatuses: { value: LibraryStatus; label: string }[] = [
  { value: 'WATCHING', label: 'Watching' },
  { value: 'PLAN_TO_WATCH', label: 'Plan to watch' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'ON_HOLD', label: 'On hold' },
  { value: 'DROPPED', label: 'Dropped' },
]

export const libraryStatusLabel = Object.fromEntries(libraryStatuses.map((s) => [s.value, s.label])) as Record<
  LibraryStatus,
  string
>
