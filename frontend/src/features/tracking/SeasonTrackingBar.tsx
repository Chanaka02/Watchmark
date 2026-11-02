import { ListChecks } from 'lucide-react'
import { Link } from 'react-router'

import { Button } from '../../components/Button'
import { ErrorMessage } from '../../components/ErrorMessage'
import { watchedMessage } from './messages'
import type { SeasonProgress } from './types'
import type { TrackingNotice } from './UndoNotice'
import { useWatchSeason } from './useTracking'

const stateLabel = {
  NOT_STARTED: null,
  IN_PROGRESS: 'In progress',
  CAUGHT_UP: 'Caught up',
  COMPLETE: 'Complete',
} as const

/** Season summary on the season page, with "Mark season watched". */
export function SeasonTrackingBar({
  showRef,
  inLibrary,
  progress,
  onNotice,
}: {
  showRef: string
  inLibrary: boolean
  progress: SeasonProgress | null | undefined
  onNotice: (notice: TrackingNotice) => void
}) {
  const watchSeason = useWatchSeason(showRef)

  if (!inLibrary) {
    return (
      <p className="rounded-card border border-line bg-surface p-4 text-sm text-ink-muted">
        <Link to={`/shows/${showRef}`} className="text-accent underline">
          Add this show to your library
        </Link>{' '}
        to track the episodes you watch.
      </p>
    )
  }
  if (!progress) {
    return null
  }
  const label = stateLabel[progress.state]
  const remaining = progress.eligible - progress.watched

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-card border border-line bg-surface p-4">
        <p className="text-sm">
          <span className="font-medium">
            {progress.watched} of {progress.eligible} aired episodes watched
          </span>
          {label && <span className="ml-2 rounded bg-surface-raised px-1.5 py-0.5 text-xs">{label}</span>}
        </p>
        <Button
          variant="secondary"
          disabled={remaining === 0}
          pending={watchSeason.isPending}
          onClick={() =>
            watchSeason.mutate(progress.number, {
              onSuccess: (result) => onNotice({ message: watchedMessage(result), operationId: result.operationId }),
            })
          }
        >
          <ListChecks aria-hidden="true" className="size-4" />
          Mark season watched
        </Button>
      </div>
      {watchSeason.isError && <ErrorMessage title="Could not mark the season">{watchSeason.error.message}</ErrorMessage>}
    </div>
  )
}
