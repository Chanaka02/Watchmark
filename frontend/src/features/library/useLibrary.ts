import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError, apiDelete, apiGet, apiPost, apiPut } from '../../lib/api'
import type { LibraryEntry, LibraryPage, LibrarySort, LibraryStatus } from './types'

const base = '/api/v1/library'

const path = (ref: string) => `${base}/${encodeURIComponent(ref)}`

export const libraryKeys = {
  all: ['library'] as const,
  list: (status: LibraryStatus | null, sort: LibrarySort) => ['library', 'list', status, sort] as const,
  entry: (ref: string) => ['library', 'entry', ref] as const,
}

export function useLibrary(status: LibraryStatus | null, sort: LibrarySort) {
  const params = new URLSearchParams({ sort })
  if (status) {
    params.set('status', status)
  }
  return useQuery({
    queryKey: libraryKeys.list(status, sort),
    queryFn: ({ signal }) => apiGet<LibraryPage>(`${base}?${params}`, { signal }),
  })
}

/** The user's entry for a show, or `null` when it is not in their library. */
export function useLibraryEntry(ref: string) {
  return useQuery({
    queryKey: libraryKeys.entry(ref),
    queryFn: async ({ signal }) => {
      try {
        return await apiGet<LibraryEntry>(path(ref), { signal })
      } catch (error) {
        if (error instanceof ApiError && error.type === 'not-in-library') {
          return null
        }
        throw error
      }
    },
  })
}

/** Shared cache handling: the entry query gets the result, every list is refetched. */
function useLibraryMutation<TInput>(ref: string, request: (input: TInput) => Promise<LibraryEntry | null>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (entry) => {
      queryClient.setQueryData(libraryKeys.entry(ref), entry)
      void queryClient.invalidateQueries({ queryKey: ['library', 'list'] })
      // Progress exists only while the show is in the library (same key as trackingKeys.show).
      void queryClient.invalidateQueries({ queryKey: ['tracking', ref] })
    },
  })
}

export function useSetStatus(ref: string) {
  return useLibraryMutation(ref, (status: LibraryStatus) => apiPut<LibraryEntry>(path(ref), { body: { status } }))
}

export function useSetRating(ref: string) {
  return useLibraryMutation(ref, (rating: number | null) =>
    apiPut<LibraryEntry>(`${path(ref)}/rating`, { body: { rating } }),
  )
}

export function useSetNotes(ref: string) {
  return useLibraryMutation(ref, (notes: string | null) => apiPut<LibraryEntry>(`${path(ref)}/notes`, { body: { notes } }))
}

/** Removes from the library; history, rating and notes are kept (R10). */
export function useRemoveFromLibrary(ref: string) {
  return useLibraryMutation(ref, () => apiDelete<void>(path(ref)).then(() => null))
}

/** Permanently deletes the user's data for this show (R11). */
export function usePurgeShowData(ref: string) {
  return useLibraryMutation(ref, (confirmName: string) =>
    apiPost<void>(`${path(ref)}/purge`, { body: { confirmName } }).then(() => null),
  )
}
