/**
 * Air dates are calendar dates (`YYYY-MM-DD`) with no time of day: TMDB does
 * not publish air times. They are therefore never shifted through time zones.
 * The user's time zone only decides what "today" is.
 */

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/

export type AirStatus = 'aired' | 'today' | 'upcoming' | 'unknown'

/** Today's date in the given IANA time zone, as `YYYY-MM-DD`. */
export function todayIn(timeZone: string, now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' })
    .formatToParts(now)
    .reduce<Record<string, string>>((acc, part) => ({ ...acc, [part.type]: part.value }), {})
  return `${parts.year}-${parts.month}-${parts.day}`
}

/** ISO date strings compare correctly as strings. */
export function airStatus(airDate: string | null, today: string): AirStatus {
  if (!airDate || !ISO_DATE.test(airDate)) {
    return 'unknown'
  }
  if (airDate === today) {
    return 'today'
  }
  return airDate < today ? 'aired' : 'upcoming'
}

/** e.g. "14 Mar 2021"; `null` for missing or malformed dates. */
export function formatDate(isoDate: string | null, locale?: string): string | null {
  const match = isoDate ? ISO_DATE.exec(isoDate) : null
  if (!match) {
    return null
  }
  const [, year, month, day] = match
  // Interpret as a calendar date in UTC so no time zone can move it a day.
  const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)))
  return new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(
    date,
  )
}

export function yearOf(isoDate: string | null): string | null {
  const match = isoDate ? ISO_DATE.exec(isoDate) : null
  return match ? match[1] : null
}

/** The calendar date (`YYYY-MM-DD`) of an instant in the given time zone. */
export function dateIn(instant: string, timeZone: string): string {
  return todayIn(timeZone, new Date(instant))
}

/** `YYYY-MM-DD` plus a number of days (negative to go back). */
export function addDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10)
}

/** "Today", "Tomorrow", "Yesterday", or the formatted date. */
export function relativeDay(isoDate: string, today: string, locale?: string): string {
  if (isoDate === today) {
    return 'Today'
  }
  if (isoDate === addDays(today, 1)) {
    return 'Tomorrow'
  }
  if (isoDate === addDays(today, -1)) {
    return 'Yesterday'
  }
  return formatDate(isoDate, locale) ?? isoDate
}

/** Time of day of an instant in the given time zone, e.g. "20:15". */
export function timeIn(instant: string, timeZone: string, locale?: string): string {
  return new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit', timeZone }).format(new Date(instant))
}
