import { CircleCheck, X } from 'lucide-react'

import { Button } from '../../components/Button'
import { useUndo } from './useTracking'

/** The result of the latest tracking action; `operationId` is null when nothing can be undone. */
export type TrackingNotice = { message: string; operationId: string | null }

/**
 * Confirms the latest tracking action and offers to undo it. It stays until
 * dismissed or replaced (no timeout, so nobody has to race it), and it is a
 * polite live region so screen readers announce it without moving focus.
 */
export function UndoNotice({
  showRef,
  notice,
  onChange,
}: {
  showRef: string
  notice: TrackingNotice | null
  onChange: (notice: TrackingNotice | null) => void
}) {
  const undo = useUndo(showRef)

  return (
    <div role="status" aria-live="polite">
      {notice && (
        <div className="flex flex-wrap items-center gap-3 rounded-card border border-line bg-surface-raised px-4 py-2 text-sm">
          <CircleCheck aria-hidden="true" className="size-4 shrink-0 text-success" />
          <span className="min-w-0 flex-1">
            {notice.message}
            {undo.isError && <span className="block text-danger">{undo.error.message}</span>}
          </span>
          {notice.operationId && (
            <Button
              variant="secondary"
              className="h-8 px-3"
              pending={undo.isPending}
              onClick={() =>
                undo.mutate(notice.operationId!, {
                  onSuccess: () => {
                    undo.reset()
                    onChange({ message: 'Undone.', operationId: null })
                  },
                })
              }
            >
              Undo
            </Button>
          )}
          <button
            type="button"
            onClick={() => {
              undo.reset()
              onChange(null)
            }}
            className="rounded p-1 text-ink-muted hover:text-ink"
          >
            <X aria-hidden="true" className="size-4" />
            <span className="sr-only">Dismiss</span>
          </button>
        </div>
      )}
    </div>
  )
}
