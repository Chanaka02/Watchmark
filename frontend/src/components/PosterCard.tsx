import { Star } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'

import type { ShowSummary } from '../features/catalog/types'
import { yearOf } from '../lib/dates'
import { Poster } from './Poster'

export function PosterCard({ show }: { show: ShowSummary }) {
  const year = yearOf(show.firstAirDate)
  return (
    <Link to={`/shows/${show.ref}`} className="group block rounded-card">
      <Poster
        url={show.posterUrl}
        alt=""
        title={show.name}
        className="transition-transform duration-200 group-hover:-translate-y-1 motion-reduce:transform-none"
      />
      <div className="mt-2 space-y-0.5">
        <p className="line-clamp-2 text-sm font-medium group-hover:text-accent">{show.name}</p>
        <p className="flex items-center gap-2 text-xs text-ink-muted">
          <span>{year ?? 'Date unknown'}</span>
          {show.voteAverage != null && show.voteAverage > 0 && (
            <span className="flex items-center gap-0.5">
              <Star aria-hidden="true" className="size-3 fill-accent text-accent" />
              <span>
                <span className="sr-only">Rating </span>
                {show.voteAverage.toFixed(1)}
              </span>
            </span>
          )}
        </p>
      </div>
    </Link>
  )
}

export function PosterGrid({ children }: { children: ReactNode }) {
  return <ul className="grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">{children}</ul>
}

export function PosterGridSkeleton({ count = 12 }: { count?: number }) {
  return (
    <div role="status" aria-label="Loading shows">
      <PosterGrid>
        {Array.from({ length: count }, (_, index) => (
          <li key={index} aria-hidden="true">
            <div className="aspect-[2/3] animate-pulse rounded-card bg-surface-raised motion-reduce:animate-none" />
            <div className="mt-2 h-4 w-3/4 animate-pulse rounded bg-surface-raised motion-reduce:animate-none" />
          </li>
        ))}
      </PosterGrid>
    </div>
  )
}
