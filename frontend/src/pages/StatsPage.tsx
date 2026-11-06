import { BarChart3 } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'

import { buttonClasses } from '../components/buttonStyles'
import { EmptyState } from '../components/EmptyState'
import { ErrorMessage } from '../components/ErrorMessage'
import { PageLoader } from '../components/PageLoader'
import { ActivityChart } from '../features/stats/ActivityChart'
import type { Stats, StatsRange } from '../features/stats/types'
import { formatMinutes, useStats } from '../features/stats/useStats'
import { formatDate } from '../lib/dates'

const ranges: { value: StatsRange; label: string }[] = [
  { value: 'week', label: 'Last 7 days' },
  { value: 'month', label: 'Last 30 days' },
  { value: 'all', label: 'All time' },
]

/** Viewing statistics: counted from what the user marked watched, nothing estimated. */
export function StatsPage() {
  const [params, setParams] = useSearchParams()
  const range = ranges.find((r) => r.value === params.get('range'))?.value ?? 'month'
  const stats = useStats(range)

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-3xl font-bold tracking-tight">Statistics</h1>
          <p className="text-sm text-ink-muted">Rewatches count as viewings; removed shows still count.</p>
        </div>
        <div role="group" aria-label="Period" className="flex gap-1 rounded-lg border border-line p-1">
          {ranges.map((option) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={option.value === range}
              onClick={() => setParams(new URLSearchParams({ range: option.value }))}
              className={`rounded-md px-3 py-1.5 text-sm font-medium ${
                option.value === range ? 'bg-accent text-accent-ink' : 'text-ink-muted hover:text-ink'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      {stats.isPending ? (
        <PageLoader label="Loading your statistics…" />
      ) : !stats.data ? (
        <ErrorMessage title="Could not load your statistics" onRetry={() => void stats.refetch()}>
          {stats.error?.message}
        </ErrorMessage>
      ) : (
        <StatsView stats={stats.data} />
      )}
    </section>
  )
}

function StatsView({ stats }: { stats: Stats }) {
  const period = stats.from ? `${formatDate(stats.from)} – ${formatDate(stats.to)}` : `Up to ${formatDate(stats.to)}`

  return (
    <div className="space-y-8">
      <p className="text-sm text-ink-muted">{period}</p>

      <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Tile label="Episodes watched" value={stats.uniqueEpisodes.toLocaleString()}>
          {stats.totalWatches.toLocaleString()} viewing{stats.totalWatches === 1 ? '' : 's'} including rewatches
        </Tile>
        <Tile label="Time watched" value={formatMinutes(stats.minutesWatched)}>
          {stats.watchesWithUnknownRuntime > 0
            ? `${stats.watchesWithUnknownRuntime} viewing${stats.watchesWithUnknownRuntime === 1 ? '' : 's'} with unknown runtime not included`
            : 'From each episode’s runtime'}
        </Tile>
        <Tile label="Shows watched" value={stats.showsWatched.toLocaleString()}>
          With at least one viewing
        </Tile>
        <Tile label="In your library" value={`${stats.library.watching} watching`}>
          {stats.library.completed} completed
        </Tile>
      </dl>

      {stats.totalWatches === 0 ? (
        <EmptyState icon={BarChart3} title="No viewings in this period">
          <p>Statistics come from the episodes you mark as watched.</p>
          <Link to="/library" className={`${buttonClasses('primary')} mt-4`}>
            Go to your library
          </Link>
        </EmptyState>
      ) : (
        <>
          <Section id="activity-heading" title={stats.activity.unit === 'DAY' ? 'Viewings per day' : 'Viewings per month'}>
            <ActivityChart activity={stats.activity} />
          </Section>
          <div className="grid gap-8 lg:grid-cols-2">
            <Section id="shows-heading" title="Most watched shows">
              <RankedList
                items={stats.topShows.map((s) => ({
                  key: s.show.ref,
                  name: (
                    <Link to={`/shows/${s.show.ref}`} className="hover:text-accent">
                      {s.show.name}
                    </Link>
                  ),
                  watches: s.watches,
                  minutes: s.minutes,
                }))}
              />
            </Section>
            <Section id="genres-heading" title="Top genres">
              {stats.topGenres.length === 0 ? (
                <p className="text-sm text-ink-muted">The shows you watched have no genres listed.</p>
              ) : (
                <>
                  <RankedList
                    items={stats.topGenres.map((g) => ({ key: g.name, name: g.name, watches: g.watches, minutes: g.minutes }))}
                  />
                  <p className="text-xs text-ink-muted">A viewing counts for every genre of its show.</p>
                </>
              )}
            </Section>
          </div>
        </>
      )}
    </div>
  )
}

function Tile({ label, value, children }: { label: string; value: string; children: ReactNode }) {
  return (
    <div className="space-y-1 rounded-card border border-line bg-surface p-4">
      <dt className="text-sm text-ink-muted">{label}</dt>
      <dd className="text-2xl font-semibold tabular-nums">{value}</dd>
      <dd className="text-xs text-ink-muted">{children}</dd>
    </div>
  )
}

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="space-y-3">
      <h2 id={id} className="text-xl font-semibold">
        {title}
      </h2>
      {children}
    </section>
  )
}

/** Names with their viewings; a thin bar shows the share of the top item. */
function RankedList({ items }: { items: { key: string; name: ReactNode; watches: number; minutes: number }[] }) {
  const max = Math.max(1, ...items.map((item) => item.watches))
  return (
    <ol className="space-y-3">
      {items.map((item) => (
        <li key={item.key} className="space-y-1 text-sm">
          <div className="flex items-baseline justify-between gap-3">
            <span className="min-w-0 truncate font-medium">{item.name}</span>
            <span className="shrink-0 tabular-nums text-ink-muted">
              {item.watches} viewing{item.watches === 1 ? '' : 's'} · {formatMinutes(item.minutes)}
            </span>
          </div>
          <div aria-hidden="true" className="h-1.5 rounded-full bg-surface-raised">
            <div className="h-full rounded-full bg-accent/80" style={{ width: `${(item.watches / max) * 100}%` }} />
          </div>
        </li>
      ))}
    </ol>
  )
}
