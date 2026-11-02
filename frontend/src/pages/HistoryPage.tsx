import { History } from 'lucide-react'
import { Link } from 'react-router'

import { Button } from '../components/Button'
import { buttonClasses } from '../components/buttonStyles'
import { EmptyState } from '../components/EmptyState'
import { ErrorMessage } from '../components/ErrorMessage'
import { PageLoader } from '../components/PageLoader'
import { useToday } from '../features/catalog/useToday'
import { useHistory } from '../features/history/useHistory'
import { episodeLabel } from '../features/tracking/messages'
import { dateIn, relativeDay, timeIn } from '../lib/dates'
import { groupBy } from '../lib/groupBy'

/** Every viewing, newest first, grouped by day in the user's time zone. */
export function HistoryPage() {
  const history = useHistory()
  const { today, timeZone } = useToday()

  if (history.isPending) {
    return <PageLoader label="Loading your history…" />
  }
  // A failed "load more" keeps the pages already shown; only a failed first load replaces them.
  if (!history.data) {
    return (
      <ErrorMessage title="Could not load your history" onRetry={() => void history.refetch()}>
        {history.error?.message}
      </ErrorMessage>
    )
  }

  const items = history.data.pages.flatMap((page) => page.items)
  const days = groupBy(items, (item) => dateIn(item.watchedAt, timeZone))

  return (
    <section className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-3xl font-bold tracking-tight">History</h1>
        <p className="text-sm text-ink-muted">Every episode you have watched, including rewatches. Times are in {timeZone}.</p>
      </div>

      {items.length === 0 ? (
        <EmptyState icon={History} title="No viewing history yet">
          <p>Episodes you mark as watched appear here.</p>
          <Link to="/library" className={`${buttonClasses('primary')} mt-4`}>
            Go to your library
          </Link>
        </EmptyState>
      ) : (
        <div className="space-y-6">
          {[...days].map(([day, viewings]) => (
            <section key={day} aria-labelledby={`history-${day}`} className="space-y-2">
              <h2 id={`history-${day}`} className="text-sm font-medium text-ink-muted">
                <time dateTime={day}>{relativeDay(day, today)}</time>
              </h2>
              <ol className="divide-y divide-line rounded-card border border-line bg-surface">
                {viewings.map((item) => (
                  <li key={item.watchId} className="flex flex-wrap items-baseline gap-x-4 gap-y-1 p-3 text-sm">
                    <time dateTime={item.watchedAt} className="w-12 shrink-0 font-mono text-xs text-ink-muted">
                      {timeIn(item.watchedAt, timeZone)}
                    </time>
                    <span className="min-w-0 flex-1">
                      <Link to={`/shows/${item.show.ref}`} className="font-medium hover:text-accent">
                        {item.show.name}
                      </Link>{' '}
                      <span className="text-ink-muted">
                        {episodeLabel(item.episode.season, item.episode.episode)} · {item.episode.name}
                      </span>
                    </span>
                    <span className="flex gap-2 text-xs">
                      {item.cycle > 1 && <span className="rounded bg-surface-raised px-1.5 py-0.5">Rewatch {item.cycle - 1}</span>}
                      {!item.inLibrary && (
                        <span className="rounded bg-surface-raised px-1.5 py-0.5 text-ink-muted">Removed from library</span>
                      )}
                    </span>
                  </li>
                ))}
              </ol>
            </section>
          ))}
          {history.hasNextPage && (
            <Button variant="secondary" pending={history.isFetchingNextPage} onClick={() => void history.fetchNextPage()}>
              Load more
            </Button>
          )}
          {history.isFetchNextPageError && (
            <ErrorMessage title="Could not load more history" onRetry={() => void history.fetchNextPage()} />
          )}
        </div>
      )}
    </section>
  )
}
