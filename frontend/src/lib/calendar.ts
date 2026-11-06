import { addDays } from './dates'

/**
 * Month grids on calendar dates (`YYYY-MM-DD`, months as `YYYY-MM`). Weeks
 * start on Monday (ISO 8601). Pure string/UTC arithmetic, so no time zone can
 * move a day.
 */

const MONTH = /^(\d{4})-(\d{2})$/

/** Day of the week, Monday = 0 … Sunday = 6. */
export function weekdayIndex(isoDate: string): number {
  const [year, month, day] = isoDate.split('-').map(Number)
  return (new Date(Date.UTC(year, month - 1, day)).getUTCDay() + 6) % 7
}

export function monthOf(isoDate: string): string {
  return isoDate.slice(0, 7)
}

export function isMonth(value: string | null): value is string {
  return value != null && MONTH.test(value) && Number(value.slice(5)) >= 1 && Number(value.slice(5)) <= 12
}

export function addMonths(month: string, months: number): string {
  const [year, number] = month.split('-').map(Number)
  return new Date(Date.UTC(year, number - 1 + months, 1)).toISOString().slice(0, 7)
}

/** Whole weeks (Monday to Sunday) covering the month: 4 to 6 rows of 7 dates. */
export function monthGrid(month: string): string[][] {
  const firstOfMonth = `${month}-01`
  const lastOfMonth = addDays(`${addMonths(month, 1)}-01`, -1)
  let day = addDays(firstOfMonth, -weekdayIndex(firstOfMonth))
  const end = addDays(lastOfMonth, 6 - weekdayIndex(lastOfMonth))
  const weeks: string[][] = []
  while (day <= end) {
    const week: string[] = []
    for (let i = 0; i < 7; i++) {
      week.push(day)
      day = addDays(day, 1)
    }
    weeks.push(week)
  }
  return weeks
}

/** e.g. "November 2026". */
export function formatMonth(month: string, locale?: string): string {
  const [year, number] = month.split('-').map(Number)
  return new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
    new Date(Date.UTC(year, number - 1, 1)),
  )
}

/** Short weekday names, Monday first, e.g. ["Mon", …, "Sun"]. */
export function weekdayNames(locale?: string, width: 'short' | 'long' = 'short'): string[] {
  // 2024-01-01 was a Monday.
  return Array.from({ length: 7 }, (_, i) =>
    new Intl.DateTimeFormat(locale, { weekday: width, timeZone: 'UTC' }).format(new Date(Date.UTC(2024, 0, 1 + i))),
  )
}
