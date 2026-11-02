import { BookmarkPlus, Trash2 } from 'lucide-react'
import { useState } from 'react'

import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { ErrorMessage } from '../../components/ErrorMessage'
import { libraryStatuses, MAX_NOTES, type LibraryEntry, type LibraryStatus } from './types'
import {
  usePurgeShowData,
  useRemoveFromLibrary,
  useSetNotes,
  useSetRating,
  useSetStatus,
  useLibraryEntry,
} from './useLibrary'

const selectClass = 'h-10 rounded-lg border border-line bg-surface px-3 text-sm text-ink'

/** Library controls on a show page: add, status, rating, notes, remove and permanent deletion. */
export function LibraryPanel({ showRef, showName }: { showRef: string; showName: string }) {
  const entry = useLibraryEntry(showRef)

  if (entry.isPending) {
    return <div className="h-10 w-48 animate-pulse rounded-lg bg-surface-raised motion-reduce:animate-none" aria-hidden="true" />
  }
  if (entry.isError) {
    return (
      <ErrorMessage title="Could not load your library" onRetry={() => void entry.refetch()}>
        {entry.error.message}
      </ErrorMessage>
    )
  }
  return entry.data ? (
    <EntryControls showRef={showRef} showName={showName} entry={entry.data} />
  ) : (
    <AddToLibrary showRef={showRef} />
  )
}

function AddToLibrary({ showRef }: { showRef: string }) {
  const [status, setStatus] = useState<LibraryStatus>('PLAN_TO_WATCH')
  const add = useSetStatus(showRef)

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <label htmlFor="add-status" className="sr-only">
          Add with status
        </label>
        <select
          id="add-status"
          value={status}
          onChange={(event) => setStatus(event.target.value as LibraryStatus)}
          className={selectClass}
        >
          {libraryStatuses.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <Button pending={add.isPending} onClick={() => add.mutate(status)}>
          <BookmarkPlus aria-hidden="true" className="size-4" />
          Add to library
        </Button>
      </div>
      {add.isError && <ErrorMessage title="Could not add this show">{add.error.message}</ErrorMessage>}
    </div>
  )
}

function EntryControls({ showRef, showName, entry }: { showRef: string; showName: string; entry: LibraryEntry }) {
  const setStatus = useSetStatus(showRef)
  const setRating = useSetRating(showRef)
  const [removeOpen, setRemoveOpen] = useState(false)
  const [purgeOpen, setPurgeOpen] = useState(false)
  const failure = setStatus.error ?? setRating.error

  return (
    <section aria-label="Your library" className="space-y-4 rounded-card border border-line bg-canvas/60 p-4">
      <div className="flex flex-wrap items-end gap-4">
        <div className="space-y-1">
          <label htmlFor="library-status" className="block text-xs font-medium text-ink-muted">
            Status
          </label>
          <select
            id="library-status"
            value={entry.status}
            disabled={setStatus.isPending}
            onChange={(event) => setStatus.mutate(event.target.value as LibraryStatus)}
            className={selectClass}
          >
            {libraryStatuses.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <label htmlFor="library-rating" className="block text-xs font-medium text-ink-muted">
            Your rating
          </label>
          <select
            id="library-rating"
            value={entry.rating ?? ''}
            disabled={setRating.isPending}
            onChange={(event) => setRating.mutate(event.target.value === '' ? null : Number(event.target.value))}
            className={selectClass}
          >
            <option value="">No rating</option>
            {Array.from({ length: 10 }, (_, index) => 10 - index).map((value) => (
              <option key={value} value={value}>
                {value} / 10
              </option>
            ))}
          </select>
        </div>
        <Button variant="ghost" onClick={() => setRemoveOpen(true)}>
          Remove from library
        </Button>
      </div>
      {failure && <ErrorMessage title="Could not save your change">{failure.message}</ErrorMessage>}

      <NotesEditor key={entry.notes ?? ''} showRef={showRef} saved={entry.notes} />

      <button
        type="button"
        onClick={() => setPurgeOpen(true)}
        className="inline-flex items-center gap-1 rounded text-xs text-ink-muted hover:text-danger"
      >
        <Trash2 aria-hidden="true" className="size-3.5" />
        Delete my data for this show
      </button>

      <RemoveDialog open={removeOpen} onClose={() => setRemoveOpen(false)} showRef={showRef} showName={showName} />
      <PurgeDialog open={purgeOpen} onClose={() => setPurgeOpen(false)} showRef={showRef} showName={showName} />
    </section>
  )
}

/** Keyed by the saved notes, so a successful save (or another tab's) resets the draft. */
function NotesEditor({ showRef, saved }: { showRef: string; saved: string | null }) {
  const [draft, setDraft] = useState(saved ?? '')
  const save = useSetNotes(showRef)
  const unchanged = draft.trim() === (saved ?? '')

  return (
    <div className="space-y-2">
      <label htmlFor="library-notes" className="block text-xs font-medium text-ink-muted">
        Private notes
      </label>
      <textarea
        id="library-notes"
        value={draft}
        maxLength={MAX_NOTES}
        rows={3}
        onChange={(event) => setDraft(event.target.value)}
        aria-describedby="library-notes-count"
        className="w-full rounded-lg border border-line bg-surface p-3 text-sm text-ink"
      />
      <div className="flex items-center justify-between gap-2">
        <span id="library-notes-count" className="text-xs text-ink-muted">
          {draft.length} / {MAX_NOTES}
        </span>
        <Button variant="secondary" disabled={unchanged} pending={save.isPending} onClick={() => save.mutate(draft)}>
          Save notes
        </Button>
      </div>
      {save.isError && <ErrorMessage title="Could not save notes">{save.error.message}</ErrorMessage>}
    </div>
  )
}

type DialogControlProps = { open: boolean; onClose: () => void; showRef: string; showName: string }

function RemoveDialog({ open, onClose, showRef, showName }: DialogControlProps) {
  const remove = useRemoveFromLibrary(showRef)
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={`Remove ${showName} from your library?`}
      actions={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button pending={remove.isPending} onClick={() => remove.mutate(undefined, { onSuccess: onClose })}>
            Remove
          </Button>
        </>
      }
    >
      <p>
        Your rating, notes and viewing history are kept. Adding the show again restores them.
      </p>
      {remove.isError && <ErrorMessage title="Could not remove the show">{remove.error.message}</ErrorMessage>}
    </Dialog>
  )
}

function PurgeDialog({ open, onClose, showRef, showName }: DialogControlProps) {
  const purge = usePurgeShowData(showRef)
  const [typed, setTyped] = useState('')
  const normalise = (value: string) => value.trim().replace(/\s+/g, ' ').toLowerCase()
  const matches = normalise(typed) === normalise(showName)

  function close() {
    setTyped('')
    purge.reset()
    onClose()
  }

  return (
    <Dialog
      open={open}
      onClose={close}
      title="Permanently delete your data for this show?"
      actions={
        <>
          <Button variant="secondary" onClick={close}>
            Cancel
          </Button>
          <Button
            disabled={!matches}
            pending={purge.isPending}
            onClick={() => purge.mutate(typed, { onSuccess: close })}
            className="bg-danger text-canvas hover:bg-danger/90"
          >
            Delete permanently
          </Button>
        </>
      }
    >
      <p>
        This deletes your status, rating, notes and viewing history for <strong className="text-ink">{showName}</strong>.
        It cannot be undone.
      </p>
      <label htmlFor="purge-confirm" className="block font-medium text-ink">
        Type the show name to confirm
      </label>
      <input
        id="purge-confirm"
        value={typed}
        autoComplete="off"
        onChange={(event) => setTyped(event.target.value)}
        className="h-10 w-full rounded-lg border border-line bg-canvas px-3 text-ink"
      />
      {purge.isError && <ErrorMessage title="Could not delete your data">{purge.error.message}</ErrorMessage>}
    </Dialog>
  )
}
