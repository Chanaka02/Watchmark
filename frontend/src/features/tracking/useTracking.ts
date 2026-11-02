import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError, apiDelete, apiGet, apiPost, apiPut } from '../../lib/api'
import type { LibraryEntry } from '../library/types'
import { libraryKeys } from '../library/useLibrary'
import type { SeasonProgress, ShowProgress, UndoResult, UnwatchResult, WatchResult } from './types'

const library = (ref: string) => `/api/v1/library/${encodeURIComponent(ref)}`

export const trackingKeys = {
  /** Everything tracked for one show: invalidated after every tracking change. */
  show: (ref: string) => ['tracking', ref] as const,
  progress: (ref: string) => ['tracking', ref, 'progress'] as const,
  season: (ref: string, season: number) => ['tracking', ref, 'season', season] as const,
}

/** Progress of a show in the library; only fetched while it is in the library. */
export function useShowProgress(ref: string, enabled: boolean) {
  return useQuery({
    queryKey: trackingKeys.progress(ref),
    queryFn: ({ signal }) => apiGet<ShowProgress>(`${library(ref)}/progress`, { signal }),
    enabled,
  })
}

/** One season's episodes with their tracking state, or `null` when none are stored yet. */
export function useSeasonProgress(ref: string, season: number, enabled: boolean) {
  return useQuery({
    queryKey: trackingKeys.season(ref, season),
    queryFn: async ({ signal }) => {
      try {
        return await apiGet<SeasonProgress>(`${library(ref)}/progress/seasons/${season}`, { signal })
      } catch (error) {
        if (error instanceof ApiError && error.type === 'season-not-found') {
          return null
        }
        throw error
      }
    },
    enabled,
  })
}

/**
 * Shared cache handling: tracking can change progress, the library entry
 * (status automation) and the library lists, so all of them are refetched.
 */
function useTrackingMutation<TInput, TResult>(ref: string, request: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: trackingKeys.show(ref) })
      void queryClient.invalidateQueries({ queryKey: libraryKeys.entry(ref) })
      void queryClient.invalidateQueries({ queryKey: ['library', 'list'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      void queryClient.invalidateQueries({ queryKey: ['history'] })
    },
  })
}

export function useWatchEpisode(ref: string) {
  return useTrackingMutation(ref, (episodeId: number) => apiPut<WatchResult>(`/api/v1/tracking/episodes/${episodeId}/watch`))
}

export function useUnwatchEpisode(ref: string) {
  return useTrackingMutation(ref, (episodeId: number) =>
    apiDelete<UnwatchResult>(`/api/v1/tracking/episodes/${episodeId}/watch`),
  )
}

export function useWatchUpTo(ref: string) {
  return useTrackingMutation(ref, (episodeId: number) =>
    apiPost<WatchResult>(`/api/v1/tracking/episodes/${episodeId}/watch-up-to`),
  )
}

export function useWatchSeason(ref: string) {
  return useTrackingMutation(ref, (season: number) =>
    apiPost<WatchResult>(`/api/v1/tracking/shows/${encodeURIComponent(ref)}/seasons/${season}/watch`),
  )
}

export function useUndo(ref: string) {
  return useTrackingMutation(ref, (operationId: string) =>
    apiPost<UndoResult>(`/api/v1/tracking/operations/${encodeURIComponent(operationId)}/undo`),
  )
}

/** Starts a new watch-through; cannot be undone. */
export function useRewatch(ref: string) {
  return useTrackingMutation(ref, () => apiPost<LibraryEntry>(`${library(ref)}/rewatch`))
}
