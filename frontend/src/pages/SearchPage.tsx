import { Search, SearchX } from 'lucide-react'
import type { FormEvent } from 'react'
import { useSearchParams } from 'react-router'

import { EmptyState } from '../components/EmptyState'
import { MockDataNotice } from '../features/catalog/MockDataNotice'
import { ShowResults } from '../features/catalog/ShowResults'
import { useSearch } from '../features/catalog/useCatalog'

const MAX_QUERY_LENGTH = 100

export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const query = params.get('q') ?? ''
  const results = useSearch(query)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const next = String(new FormData(event.currentTarget).get('q') ?? '').trim()
    setParams(next ? { q: next } : {})
  }

  return (
    <section className="space-y-6">
      <h1 className="text-3xl font-bold tracking-tight">Search</h1>
      <MockDataNotice />
      {/* Keyed by the query so back/forward navigation resets the input. */}
      <form key={query} role="search" onSubmit={submit} className="flex max-w-xl gap-2">
        <label htmlFor="search-query" className="sr-only">
          Search for a TV series
        </label>
        <input
          id="search-query"
          name="q"
          type="search"
          defaultValue={query}
          maxLength={MAX_QUERY_LENGTH}
          placeholder="Search for a TV series"
          autoFocus
          className="h-11 flex-1 rounded-lg border border-line bg-surface px-3 text-ink placeholder:text-ink-muted/60"
        />
        <button
          type="submit"
          className="inline-flex h-11 items-center gap-2 rounded-lg bg-accent px-4 text-sm font-semibold text-accent-ink hover:bg-accent/90"
        >
          <Search aria-hidden="true" className="size-4" />
          Search
        </button>
      </form>

      {query.trim() === '' ? (
        <EmptyState icon={Search} title="Find a series">
          Search by title to see details, seasons and episodes.
        </EmptyState>
      ) : (
        <div aria-live="polite" className="space-y-4">
          {results.data && (
            <p className="text-sm text-ink-muted">
              {results.data.pages[0].totalResults === 1
                ? '1 result'
                : `${results.data.pages[0].totalResults.toLocaleString()} results`}{' '}
              for “{query}”
            </p>
          )}
          <ShowResults
            query={results}
            errorTitle="Search failed"
            empty={
              <EmptyState icon={SearchX} title={`No series match “${query}”`}>
                Check the spelling or try a shorter title.
              </EmptyState>
            }
          />
        </div>
      )}
    </section>
  )
}
