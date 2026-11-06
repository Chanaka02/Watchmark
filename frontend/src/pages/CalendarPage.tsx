import { CalendarDays, CircleCheck, List } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'

import { Button } from '../components/Button'
import { ErrorMessage } from '../components/ErrorMessage'
import { PageLoader } from '../components/PageLoader'
import type { CalendarEpisode, UnscheduledShow } from '../features/calendar/types'
import { useCalendar } from '../features/calendar/useCalendar'
import { useToday } from '../features/catalog/useToday'
import { episodeLabel } from '../features/tracking/messages'
import { addMonths, formatMonth, isMonth, monthGrid, monthOf, weekdayNames } from '../lib/calendar'
import { addDays, formatDate, relativeDay } from '../lib/dates'
import { groupBy } from '../lib/groupBy'

const LIST_DAYS = 28
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/
const UNSCHEDULED_SHOWN = 5

type View = 'list' | 'month'

/**
 * Episodes of followed shows by air date, as a four-week list or a month grid.
 * Air dates have no time of day; the user's time zone only decides "today".
 */
export function CalendarPage() {
  const [params, setParams] = useSearchParams()
  const { today, timeZone } = useToday()
  const view: View = params.get('view') === 'month' ? 'month' : 'list'
  const month = isMonth(params.get('month')) ? params.get('month')! : monthOf(today)
  const listFrom = ISO_DATE.test(params.get('from') ?? '') ? params.get('from')! : today
  const weeks = monthGrid(month)
  const [from, to] = view === 'month' ? [weeks[0][0], weeks.at(-1)![6]] : [listFrom, addDays(listFrom, LIST_DAYS - 1)]
  const calendar = useCalendar(from, to)

  const go = (next: Record<string, string>) => setParams(new URLSearchParams({ view, ...next }))

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-3xl font-bold tracking-tight">Calendar</h1>
          <p className="text-sm text-ink-muted">
            Air dates of the shows you follow. Times are not published, only dates; "today" is based on {timeZone}.
          </p>
        </div>
        <div role="group" aria-label="Calendar view" className="flex gap-1 rounded-lg border border-line p-1">
          <ViewButton active={view === 'list'} onClick={() => setParams(new URLSearchParams({ view: 'list' }))}>
            <List aria-hidden="true" className="size-4" /> List
          </ViewButton>
          <ViewButton active={view === 'month'} onClick={() => setParams(new URLSearchParams({ view: 'month' }))}>
            <CalendarDays aria-hidden="true" className="size-4" /> Month
          </ViewButton>
        </div>
      </div>

      <nav aria-label="Calendar dates" className="flex flex-wrap items-center gap-2">
        {view === 'month' ? (
          <>
            <Button variant="secondary" onClick={() => go({ month: addMonths(month, -1) })}>
              Previous month
            </Button>
            <Button variant="ghost" onClick={() => go({ month: monthOf(today) })} disabled={month === monthOf(today)}>
              This month
            </Button>
            <Button variant="secondary" onClick={() => go({ month: addMonths(month, 1) })}>
              Next month
            </Button>
            <h2 className="ml-2 text-lg font-semibold" aria-live="polite">
              {formatMonth(month)}
            </h2>
          </>
        ) : (
          <>
            <Button variant="secondary" onClick={() => go({ from: addDays(listFrom, -LIST_DAYS) })}>
              Previous 4 weeks
            </Button>
            <Button variant="ghost" onClick={() => go({})} disabled={listFrom === today}>
              Today
            </Button>
            <Button variant="secondary" onClick={() => go({ from: addDays(listFrom, LIST_DAYS) })}>
              Next 4 weeks
            </Button>
            <p className="ml-2 text-sm text-ink-muted" aria-live="polite">
              {formatDate(from)} – {formatDate(to)}
            </p>
          </>
        )}
      </nav>

      {calendar.isPending ? (
        <PageLoader label="Loading the calendar…" />
      ) : !calendar.data ? (
        <ErrorMessage title="Could not load the calendar" onRetry={() => void calendar.refetch()}>
          {calendar.error?.message}
        </ErrorMessage>
      ) : (
        <>
          {view === 'month' ? (
            <>
              <div className="hidden sm:block">
                <MonthGrid month={month} weeks={weeks} today={today} episodes={calendar.data.episodes} />
              </div>
              {/* Seven columns do not fit a phone: the same days as a list instead. */}
              <div className="sm:hidden">
                <DayList episodes={calendar.data.episodes} today={today} empty="Nothing airs this month for the shows you follow." />
              </div>
            </>
          ) : (
            <DayList
              episodes={calendar.data.episodes}
              today={today}
              empty="Nothing airs in these four weeks for the shows you follow."
            />
          )}
          <Unscheduled shows={calendar.data.unscheduled} />
        </>
      )}
    </section>
  )
}

function ViewButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium ${
        active ? 'bg-accent text-accent-ink' : 'text-ink-muted hover:text-ink'
      }`}
    >
      {children}
    </button>
  )
}

function EpisodeLine({ item, compact = false }: { item: CalendarEpisode; compact?: boolean }) {
  const { show, episode, watched } = item
  return (
    <>
      <Link to={`/shows/${show.ref}`} className={`font-medium hover:text-accent ${compact ? 'block truncate' : ''}`}>
        {show.name}
      </Link>{' '}
      <span className={`text-ink-muted ${compact ? 'block truncate' : ''}`}>
        {episodeLabel(episode.season, episode.episode)}
        {!compact && ` · ${episode.name}`}
      </span>
      {watched && (
        <span className="ml-1 inline-flex items-center gap-0.5 text-xs text-success">
          <CircleCheck aria-hidden="true" className="size-3.5" />
          Watched
        </span>
      )}
    </>
  )
}

function DayList({ episodes, today, empty }: { episodes: CalendarEpisode[]; today: string; empty: string }) {
  if (episodes.length === 0) {
    return <p className="rounded-card border border-dashed border-line p-6 text-center text-sm text-ink-muted">{empty}</p>
  }
  return (
    <div className="space-y-5">
      {[...groupBy(episodes, (item) => item.episode.airDate)].map(([day, items]) => (
        <section key={day} aria-labelledby={`day-${day}`} className="space-y-2">
          <h3 id={`day-${day}`} className={`text-sm font-medium ${day === today ? 'text-accent' : 'text-ink-muted'}`}>
            <time dateTime={day}>{relativeDay(day, today)}</time>
          </h3>
          <ul className="divide-y divide-line rounded-card border border-line bg-surface">
            {items.map((item) => (
              <li key={item.episode.id} className="p-3 text-sm">
                <EpisodeLine item={item} />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

function MonthGrid({
  month,
  weeks,
  today,
  episodes,
}: {
  month: string
  weeks: string[][]
  today: string
  episodes: CalendarEpisode[]
}) {
  const byDay = groupBy(episodes, (item) => item.episode.airDate)
  const short = weekdayNames()
  const long = weekdayNames(undefined, 'long')
  return (
    <table className="w-full table-fixed border-collapse text-sm">
      <caption className="sr-only">{formatMonth(month)}</caption>
      <thead>
        <tr>
          {short.map((name, i) => (
            <th key={name} scope="col" abbr={long[i]} className="pb-2 text-left text-xs font-medium text-ink-muted">
              {name}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {weeks.map((week) => (
          <tr key={week[0]}>
            {week.map((day) => {
              const items = byDay.get(day) ?? []
              const outside = monthOf(day) !== month
              // Days of other months are told apart by a shaded cell, not by fainter (low-contrast) text.
              return (
                <td key={day} className={`h-28 border border-line p-1.5 align-top ${outside ? 'bg-surface-raised/50' : ''}`}>
                  <div className="flex h-full flex-col gap-1">
                    <time
                      dateTime={day}
                      aria-current={day === today ? 'date' : undefined}
                      className={`self-start rounded px-1 text-xs ${
                        day === today ? 'bg-accent font-semibold text-accent-ink' : 'text-ink-muted'
                      }`}
                    >
                      {Number(day.slice(8))}
                      <span className="sr-only"> {formatDate(day)}</span>
                    </time>
                    {items.length > 0 && (
                      <ul className="min-w-0 space-y-1">
                        {items.map((item) => (
                          <li key={item.episode.id} className="min-w-0 rounded bg-surface-raised px-1.5 py-1 text-xs">
                            <EpisodeLine item={item} compact />
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                </td>
              )
            })}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function Unscheduled({ shows }: { shows: UnscheduledShow[] }) {
  if (shows.length === 0) {
    return null
  }
  return (
    <section aria-labelledby="unscheduled-heading" className="space-y-3">
      <div className="space-y-1">
        <h2 id="unscheduled-heading" className="text-xl font-semibold">
          Date not announced
        </h2>
        <p className="text-sm text-ink-muted">
          These episodes are listed by the data source without an air date. They appear on the calendar once a date
          is published.
        </p>
      </div>
      <ul className="divide-y divide-line rounded-card border border-line bg-surface">
        {shows.map(({ show, episodes }) => (
          <li key={show.ref} className="space-y-1 p-3 text-sm">
            <Link to={`/shows/${show.ref}`} className="font-medium hover:text-accent">
              {show.name}
            </Link>
            <p className="text-ink-muted">
              {episodes
                .slice(0, UNSCHEDULED_SHOWN)
                .map((e) => episodeLabel(e.season, e.episode))
                .join(', ')}
              {episodes.length > UNSCHEDULED_SHOWN && ` and ${episodes.length - UNSCHEDULED_SHOWN} more`}
            </p>
          </li>
        ))}
      </ul>
    </section>
  )
}
