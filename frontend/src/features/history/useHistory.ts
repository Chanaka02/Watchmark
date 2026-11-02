import { useInfiniteQuery } from '@tanstack/react-query'

import { apiGet } from '../../lib/api'
import type { HistoryPage } from '../tracking/types'

export const HISTORY_PAGE_SIZE = 30

/** Viewing history, newest first, one cursor page at a time. */
export function useHistory() {
  return useInfiniteQuery({
    queryKey: ['history'],
    queryFn: ({ pageParam, signal }) => {
      const params = new URLSearchParams({ size: String(HISTORY_PAGE_SIZE) })
      if (pageParam) {
        params.set('cursor', pageParam)
      }
      return apiGet<HistoryPage>(`/api/v1/me/history?${params}`, { signal })
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })
}
