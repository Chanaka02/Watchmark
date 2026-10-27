import { CalendarClock, Clock, Star, Tv } from 'lucide-react'
import { Link, useParams } from 'react-router'

import { ErrorMessage } from '../components/ErrorMessage'
import { PageLoader } from '../components/PageLoader'
import { Poster } from '../components/Poster'
import { AirDate } from '../features/catalog/AirDate'
import { MockDataNotice } from '../features/catalog/MockDataNotice'
import { statusLabels, type SeasonSummary, type ShowDetails } from '../features/catalog/types'
import { useShow } from '../features/catalog/useCatalog'
import { useToday } from '../features/catalog/useToday'
import { ApiError } from '../lib/api'
import { yearOf } from '../lib/dates'

export function ShowPage() {
  const { ref = '' } = useParams()
  const show = useShow(ref)

  if (show.isPending) {
    return <PageLoader label="Loading show…" />
  }
  if (show.isError) {
    const notFound = show.error instanceof ApiError && show.error.status === 404
    return notFound ? (
      <ErrorMessage title="Show not found">
        It may have been removed from the catalogue. <Link to="/discover" className="text-accent underline">Discover shows</Link>
      </ErrorMessage>
    ) : (
      <ErrorMessage title="Could not load this show" onRetry={() => void show.refetch()}>
        {show.error.message}
      </ErrorMessage>
    )
  }
  return <ShowDetailsView show={show.data} />
}

function ShowDetailsView({ show }: { show: ShowDetails }) {
  const { today } = useToday()
  const years = yearRange(show)
  // Regular seasons first, specials last: that is how people browse a show.
  const seasons = [...show.seasons.filter((s) => s.seasonNumber > 0), ...show.seasons.filter((s) => s.seasonNumber === 0)]

  return (
    <article className="space-y-10">
      <MockDataNotice />
      <header className="relative overflow-hidden rounded-card border border-line bg-surface">
        {show.backdropUrl && (
          <img
            src={show.backdropUrl}
            alt=""
            className="absolute inset-0 size-full object-cover opacity-25"
            decoding="async"
          />
        )}
        <div className="relative flex flex-col gap-6 p-6 sm:flex-row sm:p-8">
          <Poster url={show.posterUrl} alt={`${show.name} poster`} title={show.name} className="w-40 shrink-0 sm:w-48" />
          <div className="space-y-4">
            <div className="space-y-1">
              <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{show.name}</h1>
              {show.originalName && show.originalName !== show.name && (
                <p className="text-ink-muted">{show.originalName}</p>
              )}
              {show.tagline && <p className="italic text-ink-muted">{show.tagline}</p>}
            </div>
            <ul className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-ink-muted">
              {years && <li>{years}</li>}
              <li>{statusLabels[show.status]}</li>
              {show.voteAverage != null && show.voteAverage > 0 && (
                <li className="flex items-center gap-1">
                  <Star aria-hidden="true" className="size-4 fill-accent text-accent" />
                  <span className="sr-only">Rating</span>
                  {show.voteAverage.toFixed(1)}
                </li>
              )}
              {show.episodeRunTimeMinutes != null && (
                <li className="flex items-center gap-1">
                  <Clock aria-hidden="true" className="size-4" />
                  {show.episodeRunTimeMinutes} min
                </li>
              )}
              {show.networks.length > 0 && (
                <li className="flex items-center gap-1">
                  <Tv aria-hidden="true" className="size-4" />
                  {show.networks.join(', ')}
                </li>
              )}
            </ul>
            {show.genres.length > 0 && (
              <ul aria-label="Genres" className="flex flex-wrap gap-2">
                {show.genres.map((genre) => (
                  <li key={genre} className="rounded-full border border-line px-3 py-0.5 text-xs">
                    {genre}
                  </li>
                ))}
              </ul>
            )}
            <p className="max-w-3xl leading-relaxed">{show.overview ?? 'No synopsis available.'}</p>
            {show.nextEpisode && (
              <p className="flex flex-wrap items-center gap-2 text-sm">
                <CalendarClock aria-hidden="true" className="size-4 text-accent" />
                <span className="font-medium">
                  Next: S{show.nextEpisode.seasonNumber} E{show.nextEpisode.episodeNumber}
                </span>
                <AirDate date={show.nextEpisode.airDate} today={today} />
              </p>
            )}
          </div>
        </div>
      </header>

      <section aria-labelledby="seasons-heading" className="space-y-4">
        <h2 id="seasons-heading" className="text-xl font-semibold">
          Seasons
        </h2>
        {seasons.length === 0 ? (
          <p className="text-ink-muted">No seasons have been announced yet.</p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {seasons.map((season) => (
              <li key={season.seasonNumber}>
                <SeasonLink showRef={show.ref} season={season} />
              </li>
            ))}
          </ul>
        )}
      </section>

      {show.cast.length > 0 && (
        <section aria-labelledby="cast-heading" className="space-y-4">
          <h2 id="cast-heading" className="text-xl font-semibold">
            Cast
          </h2>
          <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
            {show.cast.map((member, index) => (
              <li key={`${member.name}-${index}`} className="flex items-center gap-3">
                {member.profileUrl ? (
                  <img
                    src={member.profileUrl}
                    alt=""
                    loading="lazy"
                    className="size-12 shrink-0 rounded-full object-cover"
                  />
                ) : (
                  <span aria-hidden="true" className="flex size-12 shrink-0 items-center justify-center rounded-full bg-surface-raised text-sm font-semibold text-ink-muted">
                    {initials(member.name)}
                  </span>
                )}
                <span className="min-w-0">
                  <span className="block truncate text-sm font-medium">{member.name}</span>
                  {member.character && <span className="block truncate text-xs text-ink-muted">{member.character}</span>}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </article>
  )
}

function SeasonLink({ showRef, season }: { showRef: string; season: SeasonSummary }) {
  const year = yearOf(season.airDate)
  const details = [
    season.episodeCount != null ? `${season.episodeCount} episode${season.episodeCount === 1 ? '' : 's'}` : null,
    year,
  ].filter(Boolean)

  return (
    <Link
      to={`/shows/${showRef}/seasons/${season.seasonNumber}`}
      className="flex items-center justify-between gap-3 rounded-card border border-line bg-surface p-4 hover:border-ink-muted"
    >
      <span className="font-medium">{season.name}</span>
      <span className="text-sm text-ink-muted">{details.length > 0 ? details.join(' · ') : 'Details not announced'}</span>
    </Link>
  )
}

function yearRange(show: ShowDetails): string | null {
  const first = yearOf(show.firstAirDate)
  if (!first) {
    return null
  }
  const last = yearOf(show.lastAirDate)
  if (show.status === 'ENDED' || show.status === 'CANCELED') {
    return last && last !== first ? `${first}–${last}` : first
  }
  return `${first}–`
}

function initials(name: string): string {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('')
}
