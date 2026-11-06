import { CalendarClock, CircleCheck, History, LayoutDashboard } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'

import { Button } from '../../components/Button'
import { buttonClasses } from '../../components/buttonStyles'
import { EmptyState } from '../../components/EmptyState'
import { ErrorMessage } from '../../components/ErrorMessage'
import { Poster } from '../../components/Poster'
import { PosterGrid, PosterGridSkeleton } from '../../components/PosterCard'
import { dateIn, relativeDay, timeIn } from '../../lib/dates'
import { groupBy } from '../../lib/groupBy'
import { useToday } from '../catalog/useToday'
import { libraryStatusLabel } from '../library/types'
import { episodeLabel, watchedMessage } from '../tracking/messages'
import type { HistoryItem } from '../tracking/types'
import { UndoNotice, type TrackingNotice } from '../tracking/UndoNotice'
import { useWatchEpisode } from '../tracking/useTracking'
import type { ContinueWatching, Dashboard, Upcoming } from './types'
import { useDashboard } from './useDashboard'

type ShowNotice = { showRef: string; notice: TrackingNotice }

/** The signed-in home page: only the user's own data, with empty sections left out. */
export function DashboardView({ displayName }: { displayName: string }) {
  const dashboard = useDashboard()

  return (
    <div className="space-y-10">
      <h1 className="text-3xl font-bold tracking-tight">Welcome back, {displayName}.</h1>
      {dashboard.isPending ? (
        <PosterGridSkeleton />
      ) : dashboard.isError ? (
        <ErrorMessage title="Could not load your dashboard" onRetry={() => void dashboard.refetch()}>
          {dashboard.error.message}
        </ErrorMessage>
      ) : (
        <DashboardSections dashboard={dashboard.data} />
      )}
    </div>
  )
}

function DashboardSections({ dashboard }: { dashboard: Dashboard }) {
  const [shown, setShown] = useState<ShowNotice | null>(null)
  const { continueWatching, upcoming, recentlyWatched, recentlyAdded, today } = dashboard

  if (continueWatching.length + upcoming.length + recentlyWatched.length + recentlyAdded.length === 0) {
    return (
      <EmptyState icon={LayoutDashboard} title="Your dashboard is empty">
        <p>Add shows to your library and mark episodes as you watch. What to watch next and upcoming episodes then appear here.</p>
        <Link to="/discover" className={`${buttonClasses('primary')} mt-4`}>
          Discover shows
        </Link>
      </EmptyState>
    )
  }

  return (
    <>
      {shown && (
        <UndoNotice
          showRef={shown.showRef}
          notice={shown.notice}
          onChange={(notice) => setShown(notice ? { showRef: shown.showRef, notice } : null)}
        />
      )}

      <Section title="Continue watching">
        {continueWatching.length === 0 ? (
          <p className="text-sm text-ink-muted">You're caught up on everything you're watching.</p>
        ) : (
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {continueWatching.map((item) => (
              <li key={item.show.ref}>
                <ContinueCard item={item} onNotice={(notice) => setShown({ showRef: item.show.ref, notice })} />
              </li>
            ))}
          </ul>
        )}
      </Section>

      {upcoming.length > 0 && (
        <Section
          title="Upcoming"
          icon={CalendarClock}
          action={
            <Link to="/calendar" className="text-sm text-accent hover:underline">
              Calendar
            </Link>
          }
        >
          <UpcomingList items={upcoming} today={today} />
        </Section>
      )}

      {recentlyWatched.length > 0 && (
        <Section
          title="Recently watched"
          icon={History}
          action={
            <Link to="/history" className="text-sm text-accent hover:underline">
              Full history
            </Link>
          }
        >
          <RecentlyWatched items={recentlyWatched} today={today} />
        </Section>
      )}

      {recentlyAdded.length > 0 && (
        <Section title="Recently added">
          <PosterGrid>
            {recentlyAdded.map((entry) => (
              <li key={entry.show.ref}>
                <Link to={`/shows/${entry.show.ref}`} className="group block rounded-card">
                  <Poster url={entry.show.posterUrl} alt="" title={entry.show.name} />
                  <p className="mt-2 line-clamp-2 text-sm font-medium group-hover:text-accent">{entry.show.name}</p>
                  <p className="text-xs text-ink-muted">{libraryStatusLabel[entry.status]}</p>
                </Link>
              </li>
            ))}
          </PosterGrid>
        </Section>
      )}
    </>
  )
}

function Section({
  title,
  icon: Icon,
  action,
  children,
}: {
  title: string
  icon?: typeof History
  action?: ReactNode
  children: ReactNode
}) {
  const id = `dashboard-${title.toLowerCase().replace(/\s+/g, '-')}`
  return (
    <section aria-labelledby={id} className="space-y-4">
      <div className="flex items-baseline justify-between gap-4">
        <h2 id={id} className="flex items-center gap-2 text-xl font-semibold">
          {Icon && <Icon aria-hidden="true" className="size-5 text-ink-muted" />}
          {title}
        </h2>
        {action}
      </div>
      {children}
    </section>
  )
}

function ContinueCard({ item, onNotice }: { item: ContinueWatching; onNotice: (notice: TrackingNotice) => void }) {
  const watch = useWatchEpisode(item.show.ref)
  const { show, nextEpisode, progress } = item
  const label = episodeLabel(nextEpisode.season, nextEpisode.episode)

  return (
    <article aria-label={show.name} className="flex h-full gap-4 rounded-card border border-line bg-surface p-3">
      <Link to={`/shows/${show.ref}`} className="w-20 shrink-0 rounded-card" tabIndex={-1} aria-hidden="true">
        <Poster url={show.posterUrl} alt="" title={show.name} />
      </Link>
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <div className="min-w-0">
          <Link to={`/shows/${show.ref}`} className="line-clamp-1 font-medium hover:text-accent">
            {show.name}
          </Link>
          <p className="line-clamp-1 text-sm text-ink-muted">
            {label} · {nextEpisode.name}
          </p>
        </div>
        <div
          role="progressbar"
          aria-label={`${show.name} progress`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={progress.percent}
          aria-valuetext={`${progress.watched} of ${progress.eligible} aired episodes watched`}
          className="h-1.5 overflow-hidden rounded-full bg-surface-raised"
        >
          <div className="h-full rounded-full bg-accent" style={{ width: `${progress.percent}%` }} />
        </div>
        <p className="text-xs text-ink-muted">
          {progress.watched} of {progress.eligible} aired episodes
          {item.cycle > 1 && ` · Rewatch ${item.cycle - 1}`}
        </p>
        <div className="mt-auto">
          <Button
            variant="secondary"
            className="h-8 px-3 text-xs"
            pending={watch.isPending}
            onClick={() =>
              watch.mutate(nextEpisode.id, {
                onSuccess: (result) => onNotice({ message: watchedMessage(result, label), operationId: result.operationId }),
              })
            }
          >
            <CircleCheck aria-hidden="true" className="size-4" />
            Mark {label} watched
          </Button>
          {watch.isError && <p className="mt-1 text-xs text-danger">{watch.error.message}</p>}
        </div>
      </div>
    </article>
  )
}

function UpcomingList({ items, today }: { items: Upcoming[]; today: string }) {
  const days = groupBy(items, (item) => item.episode.airDate)
  return (
    <div className="space-y-4">
      {[...days].map(([day, episodes]) => (
        <div key={day} className="space-y-2">
          <h3 className="text-sm font-medium text-ink-muted">
            <time dateTime={day}>{relativeDay(day, today)}</time>
          </h3>
          <ul className="divide-y divide-line rounded-card border border-line bg-surface">
            {episodes.map(({ show, episode }) => (
              <li key={episode.id} className="flex flex-wrap items-baseline gap-x-3 gap-y-1 p-3 text-sm">
                <Link to={`/shows/${show.ref}`} className="font-medium hover:text-accent">
                  {show.name}
                </Link>
                <span className="text-ink-muted">
                  {episodeLabel(episode.season, episode.episode)} · {episode.name}
                </span>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  )
}

function RecentlyWatched({ items, today }: { items: HistoryItem[]; today: string }) {
  const { timeZone } = useToday()
  return (
    <ul className="divide-y divide-line rounded-card border border-line bg-surface">
      {items.map((item) => (
        <li key={item.watchId} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 p-3 text-sm">
          <span className="min-w-0">
            <Link to={`/shows/${item.show.ref}`} className="font-medium hover:text-accent">
              {item.show.name}
            </Link>{' '}
            <span className="text-ink-muted">
              {episodeLabel(item.episode.season, item.episode.episode)} · {item.episode.name}
            </span>
          </span>
          <time dateTime={item.watchedAt} className="text-xs text-ink-muted">
            {relativeDay(dateIn(item.watchedAt, timeZone), today)}, {timeIn(item.watchedAt, timeZone)}
          </time>
        </li>
      ))}
    </ul>
  )
}
