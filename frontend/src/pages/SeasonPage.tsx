import { ChevronLeft, Clock, ListX } from 'lucide-react'
import { Link, useParams } from 'react-router'

import { EmptyState } from '../components/EmptyState'
import { ErrorMessage } from '../components/ErrorMessage'
import { PageLoader } from '../components/PageLoader'
import { AirDate } from '../features/catalog/AirDate'
import { MockDataNotice } from '../features/catalog/MockDataNotice'
import { useSeason, useShow } from '../features/catalog/useCatalog'
import { useToday } from '../features/catalog/useToday'
import { ApiError } from '../lib/api'

export function SeasonPage() {
  const { ref = '', seasonNumber: seasonParam = '' } = useParams()
  const seasonNumber = /^\d+$/.test(seasonParam) ? Number(seasonParam) : -1
  const season = useSeason(ref, seasonNumber)
  const show = useShow(ref)
  const { today, timeZone } = useToday()

  if (seasonNumber < 0) {
    return <ErrorMessage title="Season not found" />
  }
  if (season.isPending) {
    return <PageLoader label="Loading episodes…" />
  }
  if (season.isError) {
    const notFound = season.error instanceof ApiError && season.error.status === 404
    return (
      <ErrorMessage
        title={notFound ? 'Season not found' : 'Could not load this season'}
        onRetry={notFound ? undefined : () => void season.refetch()}
      >
        {notFound ? undefined : season.error.message}
      </ErrorMessage>
    )
  }

  const { episodes } = season.data
  return (
    <article className="space-y-6">
      <MockDataNotice />
      <div className="space-y-2">
        <Link to={`/shows/${ref}`} className="inline-flex items-center gap-1 rounded text-sm text-ink-muted hover:text-ink">
          <ChevronLeft aria-hidden="true" className="size-4" />
          {show.data?.name ?? 'Back to show'}
        </Link>
        <h1 className="text-3xl font-bold tracking-tight">{season.data.name}</h1>
        {season.data.overview && <p className="max-w-3xl text-ink-muted">{season.data.overview}</p>}
        <p className="text-xs text-ink-muted">
          Release status is based on today in {timeZone}. Air times are not published, only dates.
        </p>
      </div>

      {episodes.length === 0 ? (
        <EmptyState icon={ListX} title="No episodes announced yet">
          Episodes appear here once they are published.
        </EmptyState>
      ) : (
        <ol className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
          {episodes.map((episode) => (
            <li key={episode.externalId} className="flex gap-4 p-4">
              <span className="w-10 shrink-0 text-right font-mono text-sm text-ink-muted" aria-hidden="true">
                {episode.episodeNumber}
              </span>
              <div className="min-w-0 flex-1 space-y-1">
                <h2 className="font-medium">
                  <span className="sr-only">Episode {episode.episodeNumber}: </span>
                  {episode.name}
                </h2>
                <p className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-ink-muted">
                  <AirDate date={episode.airDate} today={today} />
                  {episode.runtimeMinutes != null && (
                    <span className="inline-flex items-center gap-1">
                      <Clock aria-hidden="true" className="size-3.5" />
                      {episode.runtimeMinutes} min
                    </span>
                  )}
                </p>
                {episode.overview && <p className="text-sm leading-relaxed text-ink-muted">{episode.overview}</p>}
              </div>
            </li>
          ))}
        </ol>
      )}
    </article>
  )
}
