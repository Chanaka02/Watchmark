import { Library, Star } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'

import { buttonClasses } from '../components/buttonStyles'
import { EmptyState } from '../components/EmptyState'
import { ErrorMessage } from '../components/ErrorMessage'
import { Poster } from '../components/Poster'
import { PosterGrid, PosterGridSkeleton } from '../components/PosterCard'
import { libraryStatuses, libraryStatusLabel, type LibraryEntry, type LibrarySort, type LibraryStatus } from '../features/library/types'
import { useLibrary } from '../features/library/useLibrary'

const sorts: { value: LibrarySort; label: string }[] = [
  { value: 'RECENT', label: 'Recently updated' },
  { value: 'TITLE', label: 'Title' },
  { value: 'ADDED', label: 'Recently added' },
]

function statusFrom(value: string | null): LibraryStatus | null {
  return libraryStatuses.find((status) => status.value === value)?.value ?? null
}

function sortFrom(value: string | null): LibrarySort {
  return sorts.find((sort) => sort.value === value)?.value ?? 'RECENT'
}

export function LibraryPage() {
  const [params, setParams] = useSearchParams()
  const status = statusFrom(params.get('status'))
  const sort = sortFrom(params.get('sort'))
  const library = useLibrary(status, sort)

  const counts = library.data?.counts
  const total = counts ? Object.values(counts).reduce((sum, count) => sum + count, 0) : null

  function update(next: { status?: LibraryStatus | null; sort?: LibrarySort }) {
    const merged = new URLSearchParams(params)
    if (next.status !== undefined) {
      if (next.status) merged.set('status', next.status)
      else merged.delete('status')
    }
    if (next.sort) {
      merged.set('sort', next.sort)
    }
    setParams(merged)
  }

  const tabs: { value: LibraryStatus | null; label: string; count: number | null }[] = [
    { value: null, label: 'All', count: total },
    ...libraryStatuses.map((item) => ({ ...item, count: counts?.[item.value] ?? null })),
  ]

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="text-3xl font-bold tracking-tight">My library</h1>
        <div className="flex items-center gap-2">
          <label htmlFor="library-sort" className="text-sm text-ink-muted">
            Sort by
          </label>
          <select
            id="library-sort"
            value={sort}
            onChange={(event) => update({ sort: event.target.value as LibrarySort })}
            className="h-10 rounded-lg border border-line bg-surface px-3 text-sm text-ink"
          >
            {sorts.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <nav aria-label="Library status" className="flex gap-2 overflow-x-auto pb-1">
        {tabs.map((tab) => {
          const active = tab.value === status
          return (
            <button
              key={tab.label}
              type="button"
              aria-pressed={active}
              onClick={() => update({ status: tab.value })}
              className={`shrink-0 rounded-full border px-4 py-1.5 text-sm font-medium transition-colors ${
                active ? 'border-accent bg-accent text-accent-ink' : 'border-line text-ink-muted hover:text-ink'
              }`}
            >
              {tab.label}
              {/* The space keeps the accessible name "Watching 2", not "Watching2". */}
              {tab.count != null && (
                <>
                  {' '}
                  <span className="ml-1 opacity-70">{tab.count}</span>
                </>
              )}
            </button>
          )
        })}
      </nav>

      <LibraryResults library={library} status={status} empty={total === 0} />
    </section>
  )
}

function LibraryResults({
  library,
  status,
  empty,
}: {
  library: ReturnType<typeof useLibrary>
  status: LibraryStatus | null
  empty: boolean
}) {
  if (library.isPending) {
    return <PosterGridSkeleton />
  }
  if (library.isError) {
    return (
      <ErrorMessage title="Could not load your library" onRetry={() => void library.refetch()}>
        {library.error.message}
      </ErrorMessage>
    )
  }
  if (empty) {
    return (
      <EmptyState icon={Library} title="Your library is empty">
        <p>Find a series and add it to start tracking.</p>
        <Link to="/discover" className={`${buttonClasses('primary')} mt-4`}>
          Discover shows
        </Link>
      </EmptyState>
    )
  }
  if (library.data.entries.length === 0) {
    return (
      <EmptyState icon={Library} title={`No shows marked “${status ? libraryStatusLabel[status] : ''}”`}>
        Change a show's status from its page to see it here.
      </EmptyState>
    )
  }
  return (
    <PosterGrid>
      {library.data.entries.map((entry) => (
        <li key={entry.show.ref}>
          <LibraryCard entry={entry} />
        </li>
      ))}
    </PosterGrid>
  )
}

function LibraryCard({ entry }: { entry: LibraryEntry }) {
  return (
    <Link to={`/shows/${entry.show.ref}`} className="group block rounded-card">
      <Poster url={entry.show.posterUrl} alt="" title={entry.show.name} />
      <div className="mt-2 space-y-1">
        <p className="line-clamp-2 text-sm font-medium group-hover:text-accent">{entry.show.name}</p>
        <p className="flex flex-wrap items-center gap-2 text-xs text-ink-muted">
          <span className="rounded bg-surface-raised px-1.5 py-0.5">{libraryStatusLabel[entry.status]}</span>
          {entry.rating != null && (
            <span className="flex items-center gap-0.5">
              <Star aria-hidden="true" className="size-3 fill-accent text-accent" />
              <span>
                <span className="sr-only">Your rating </span>
                {entry.rating}/10
              </span>
            </span>
          )}
        </p>
      </div>
    </Link>
  )
}
