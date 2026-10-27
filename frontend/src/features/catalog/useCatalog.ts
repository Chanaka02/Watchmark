import { useInfiniteQuery, useQuery, type InfiniteData } from '@tanstack/react-query'

import { apiGet } from '../../lib/api'
import type { CatalogSource, DiscoverListSlug, ResultPage, SeasonDetails, ShowDetails, ShowSummary } from './types'

const base = '/api/v1/catalog'

/** Metadata changes slowly; the backend caches too. */
const CATALOG_STALE_MS = 10 * 60 * 1000

function nextPage(last: ResultPage<ShowSummary>) {
  return last.page < last.totalPages ? last.page + 1 : undefined
}

/** All items of the loaded pages, de-duplicated by ref (providers can repeat items across pages). */
export function flattenPages(data: InfiniteData<ResultPage<ShowSummary>> | undefined): ShowSummary[] {
  const seen = new Set<string>()
  const items: ShowSummary[] = []
  for (const page of data?.pages ?? []) {
    for (const item of page.items) {
      if (!seen.has(item.ref)) {
        seen.add(item.ref)
        items.push(item)
      }
    }
  }
  return items
}

export function useCatalogSource() {
  return useQuery({
    queryKey: ['catalog', 'source'],
    queryFn: ({ signal }) => apiGet<CatalogSource>(`${base}/source`, { signal }),
    staleTime: Infinity,
    retry: false,
  })
}

export function useDiscover(list: DiscoverListSlug) {
  return useInfiniteQuery({
    queryKey: ['catalog', 'discover', list],
    queryFn: ({ pageParam, signal }) =>
      apiGet<ResultPage<ShowSummary>>(`${base}/discover/${list}?page=${pageParam}`, { signal }),
    initialPageParam: 1,
    getNextPageParam: nextPage,
    staleTime: CATALOG_STALE_MS,
  })
}

export function useSearch(query: string) {
  const trimmed = query.trim()
  return useInfiniteQuery({
    queryKey: ['catalog', 'search', trimmed.toLowerCase()],
    queryFn: ({ pageParam, signal }) =>
      apiGet<ResultPage<ShowSummary>>(`${base}/search?q=${encodeURIComponent(trimmed)}&page=${pageParam}`, {
        signal,
      }),
    initialPageParam: 1,
    getNextPageParam: nextPage,
    enabled: trimmed.length > 0,
    staleTime: CATALOG_STALE_MS,
  })
}

export function useShow(ref: string) {
  return useQuery({
    queryKey: ['catalog', 'show', ref],
    queryFn: ({ signal }) => apiGet<ShowDetails>(`${base}/shows/${encodeURIComponent(ref)}`, { signal }),
    staleTime: CATALOG_STALE_MS,
  })
}

export function useSeason(ref: string, seasonNumber: number) {
  return useQuery({
    queryKey: ['catalog', 'season', ref, seasonNumber],
    queryFn: ({ signal }) =>
      apiGet<SeasonDetails>(`${base}/shows/${encodeURIComponent(ref)}/seasons/${seasonNumber}`, { signal }),
    staleTime: CATALOG_STALE_MS,
    enabled: Number.isInteger(seasonNumber) && seasonNumber >= 0,
  })
}
