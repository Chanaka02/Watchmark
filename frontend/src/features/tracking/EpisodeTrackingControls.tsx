import { Circle, CircleCheck } from 'lucide-react'

import { Button } from '../../components/Button'
import { episodeLabel, watchedMessage } from './messages'
import type { EpisodeProgress } from './types'
import type { TrackingNotice } from './UndoNotice'
import { useUnwatchEpisode, useWatchEpisode, useWatchUpTo } from './useTracking'

/**
 * Watched toggle and "Up to here" for one episode. Episodes that have not aired
 * show no controls: their air date already says why (rule R5).
 */
export function EpisodeTrackingControls({
  showRef,
  season,
  episode,
  onNotice,
  onError,
}: {
  showRef: string
  season: number
  episode: EpisodeProgress
  onNotice: (notice: TrackingNotice) => void
  onError: (message: string) => void
}) {
  const watch = useWatchEpisode(showRef)
  const unwatch = useUnwatchEpisode(showRef)
  const upTo = useWatchUpTo(showRef)
  const label = episodeLabel(season, episode.episode)
  const pending = watch.isPending || unwatch.isPending || upTo.isPending

  if (!episode.watched && !episode.eligible) {
    return null
  }

  function toggle() {
    const options = { onError: (error: Error) => onError(error.message) }
    if (episode.watched) {
      unwatch.mutate(episode.id, {
        ...options,
        onSuccess: (result) =>
          onNotice({
            message: result.removed > 0 ? `Marked ${label} unwatched.` : `${label} was not watched.`,
            operationId: result.operationId,
          }),
      })
    } else {
      watch.mutate(episode.id, {
        ...options,
        onSuccess: (result) => onNotice({ message: watchedMessage(result, label), operationId: result.operationId }),
      })
    }
  }

  return (
    <div className="flex shrink-0 flex-wrap items-center gap-2">
      {!episode.watched && !episode.upstreamMissing && (
        <Button
          variant="ghost"
          className="h-8 px-2 text-xs"
          disabled={pending}
          onClick={() =>
            upTo.mutate(episode.id, {
              onError: (error) => onError(error.message),
              onSuccess: (result) => onNotice({ message: watchedMessage(result), operationId: result.operationId }),
            })
          }
        >
          {/* The accessible name starts with the visible text (WCAG 2.5.3). */}
          Up to here<span className="sr-only">: mark everything up to {label} watched</span>
        </Button>
      )}
      <Button
        variant={episode.watched ? 'primary' : 'secondary'}
        className="h-8 px-3"
        aria-pressed={episode.watched}
        pending={pending}
        onClick={toggle}
      >
        {episode.watched ? (
          <CircleCheck aria-hidden="true" className="size-4" />
        ) : (
          <Circle aria-hidden="true" className="size-4" />
        )}
        {/* The space keeps the accessible name "S1 E2 Watched", not "S1 E2Watched". */}
        <span className="sr-only">{label}</span> Watched
      </Button>
    </div>
  )
}
