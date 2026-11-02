import { CircleCheck, Repeat } from 'lucide-react'
import { useState } from 'react'

import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { ErrorMessage } from '../../components/ErrorMessage'
import { AirDate } from '../catalog/AirDate'
import { useToday } from '../catalog/useToday'
import { episodeLabel, watchedMessage } from './messages'
import type { ShowProgress } from './types'
import { UndoNotice, type TrackingNotice } from './UndoNotice'
import { useRewatch, useShowProgress, useWatchEpisode } from './useTracking'

/** "Your progress" on a show page; rendered only while the show is in the library. */
export function ProgressPanel({ showRef, showName }: { showRef: string; showName: string }) {
  const progress = useShowProgress(showRef, true)

  if (progress.isPending) {
    return <div className="h-24 animate-pulse rounded-card bg-surface-raised motion-reduce:animate-none" aria-hidden="true" />
  }
  if (progress.isError) {
    return (
      <ErrorMessage title="Could not load your progress" onRetry={() => void progress.refetch()}>
        {progress.error.message}
      </ErrorMessage>
    )
  }
  return <ProgressDetails showRef={showRef} showName={showName} progress={progress.data} />
}

function ProgressDetails({ showRef, showName, progress }: { showRef: string; showName: string; progress: ShowProgress }) {
  const { today } = useToday()
  const watch = useWatchEpisode(showRef)
  const [notice, setNotice] = useState<TrackingNotice | null>(null)
  const [rewatchOpen, setRewatchOpen] = useState(false)
  const { overall, nextEpisode, history } = progress

  return (
    <section aria-labelledby="progress-heading" className="space-y-4 rounded-card border border-line bg-surface p-4 sm:p-6">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="progress-heading" className="text-xl font-semibold">
          Your progress
        </h2>
        {progress.cycle > 1 && <span className="text-sm text-ink-muted">Rewatch {progress.cycle - 1}</span>}
      </div>

      <div className="space-y-2">
        <div
          role="progressbar"
          aria-label="Episodes watched"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={overall.percent}
          aria-valuetext={`${overall.watched} of ${overall.eligible} aired episodes watched`}
          className="h-2 overflow-hidden rounded-full bg-surface-raised"
        >
          <div className="h-full rounded-full bg-accent" style={{ width: `${overall.percent}%` }} />
        </div>
        <p className="text-sm">
          <span className="font-medium">
            {overall.watched} of {overall.eligible} aired episodes
          </span>{' '}
          <span className="text-ink-muted">· {overall.percent}%</span>
          {overall.total > overall.eligible && (
            <span className="text-ink-muted"> · {overall.total - overall.eligible} not aired yet</span>
          )}
        </p>
        {history.totalWatches > history.uniqueEpisodes && (
          <p className="text-xs text-ink-muted">
            {history.totalWatches} viewings of {history.uniqueEpisodes} episodes across all watch-throughs.
          </p>
        )}
      </div>

      {nextEpisode ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-line p-3">
          <div className="min-w-0 space-y-0.5">
            <p className="text-xs font-medium uppercase tracking-wide text-ink-muted">Up next</p>
            <p className="font-medium">
              {episodeLabel(nextEpisode.season, nextEpisode.episode)} · {nextEpisode.name}
            </p>
            <p className="text-sm text-ink-muted">
              <AirDate date={nextEpisode.airDate} today={today} />
            </p>
          </div>
          <Button
            pending={watch.isPending}
            onClick={() =>
              watch.mutate(nextEpisode.id, {
                onSuccess: (result) =>
                  setNotice({
                    message: watchedMessage(result, episodeLabel(nextEpisode.season, nextEpisode.episode)),
                    operationId: result.operationId,
                  }),
              })
            }
          >
            <CircleCheck aria-hidden="true" className="size-4" />
            Mark watched
          </Button>
        </div>
      ) : (
        <StateMessage progress={progress} />
      )}
      {watch.isError && <ErrorMessage title="Could not mark the episode watched">{watch.error.message}</ErrorMessage>}

      {progress.canRewatch && overall.state === 'COMPLETE' && (
        <Button variant="secondary" onClick={() => setRewatchOpen(true)}>
          <Repeat aria-hidden="true" className="size-4" />
          Start a rewatch
        </Button>
      )}

      <UndoNotice showRef={showRef} notice={notice} onChange={setNotice} />
      <RewatchDialog
        showRef={showRef}
        showName={showName}
        open={rewatchOpen}
        onClose={() => setRewatchOpen(false)}
        onStarted={() => setNotice({ message: 'Rewatch started.', operationId: null })}
      />
    </section>
  )
}

function StateMessage({ progress }: { progress: ShowProgress }) {
  switch (progress.overall.state) {
    case 'COMPLETE':
      return <p className="text-sm">You have watched every episode.</p>
    case 'CAUGHT_UP':
      return <p className="text-sm">You're caught up. More episodes are on the way.</p>
    case 'NOT_STARTED':
      return (
        <p className="text-sm text-ink-muted">
          {progress.overall.eligible === 0 ? 'No episodes have aired yet.' : 'Open a season to mark episodes watched.'}
        </p>
      )
    default:
      return <p className="text-sm text-ink-muted">Open a season to mark the episodes you skipped.</p>
  }
}

function RewatchDialog({
  showRef,
  showName,
  open,
  onClose,
  onStarted,
}: {
  showRef: string
  showName: string
  open: boolean
  onClose: () => void
  onStarted: () => void
}) {
  const rewatch = useRewatch(showRef)

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={`Rewatch ${showName}?`}
      actions={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            pending={rewatch.isPending}
            onClick={() =>
              rewatch.mutate(undefined, {
                onSuccess: () => {
                  onClose()
                  onStarted()
                },
              })
            }
          >
            Start rewatch
          </Button>
        </>
      }
    >
      <p>
        Progress starts again from the first episode. Everything you watched before stays in your history and
        statistics. Starting a rewatch cannot be undone.
      </p>
      {rewatch.isError && <ErrorMessage title="Could not start the rewatch">{rewatch.error.message}</ErrorMessage>}
    </Dialog>
  )
}
