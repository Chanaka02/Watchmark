import type { UseInfiniteQueryResult, InfiniteData } from '@tanstack/react-query'
import type { ReactNode } from 'react'

import { Button } from '../../components/Button'
import { ErrorMessage } from '../../components/ErrorMessage'
import { PosterCard, PosterGrid, PosterGridSkeleton } from '../../components/PosterCard'
import { flattenPages } from './useCatalog'
import type { ResultPage, ShowSummary } from './types'

type ShowResultsProps = {
  query: UseInfiniteQueryResult<InfiniteData<ResultPage<ShowSummary>>, Error>
  /** Rendered when the first page has no results. */
  empty: ReactNode
  errorTitle: string
}

/** Paged poster grid with loading, empty, error and "load more" states. */
export function ShowResults({ query, empty, errorTitle }: ShowResultsProps) {
  if (query.isPending) {
    return <PosterGridSkeleton />
  }
  if (query.isError && !query.data) {
    return (
      <ErrorMessage title={errorTitle} onRetry={() => void query.refetch()}>
        {query.error.message}
      </ErrorMessage>
    )
  }

  const shows = flattenPages(query.data)
  if (shows.length === 0) {
    return <>{empty}</>
  }

  return (
    <div className="space-y-8">
      <PosterGrid>
        {shows.map((show) => (
          <li key={show.ref}>
            <PosterCard show={show} />
          </li>
        ))}
      </PosterGrid>
      {query.isFetchNextPageError && (
        <ErrorMessage title="Could not load more shows" onRetry={() => void query.fetchNextPage()}>
          {query.error?.message}
        </ErrorMessage>
      )}
      {query.hasNextPage && !query.isFetchNextPageError && (
        <div className="flex justify-center">
          <Button variant="secondary" pending={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>
            Load more
          </Button>
        </div>
      )}
    </div>
  )
}
