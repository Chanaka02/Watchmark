import { airStatus, formatDate } from '../../lib/dates'

const badgeStyles = {
  today: 'bg-success/15 text-success',
  upcoming: 'bg-accent/15 text-accent',
}

/**
 * An episode's air date with its status relative to the user's today.
 * Unknown dates are shown as such, never estimated.
 */
export function AirDate({ date, today }: { date: string | null; today: string }) {
  const status = airStatus(date, today)
  const formatted = formatDate(date)

  if (status === 'unknown' || !formatted) {
    return <span className="text-ink-muted italic">Date not announced</span>
  }
  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <time dateTime={date ?? undefined}>{formatted}</time>
      {status === 'today' && <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${badgeStyles.today}`}>Airs today</span>}
      {status === 'upcoming' && (
        <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${badgeStyles.upcoming}`}>Upcoming</span>
      )}
    </span>
  )
}
