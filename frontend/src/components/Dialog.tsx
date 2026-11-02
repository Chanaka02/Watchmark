import { useEffect, useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

type DialogProps = {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
  /** Buttons rendered in the footer. */
  actions: ReactNode
}

const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])'

/**
 * Modal dialog: focus moves into it on open, Tab stays inside, Escape and the
 * backdrop close it, and focus returns to the opening control afterwards.
 */
export function Dialog({ open, title, onClose, children, actions }: DialogProps) {
  const titleId = useId()
  const panel = useRef<HTMLDivElement>(null)
  // Latest onClose without re-running the focus effect on every parent render
  // (which would steal focus while the user types).
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    if (!open) {
      return
    }
    const previouslyFocused = document.activeElement as HTMLElement | null
    panel.current?.querySelector<HTMLElement>(FOCUSABLE)?.focus()

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault()
        onCloseRef.current()
        return
      }
      if (event.key !== 'Tab' || !panel.current) {
        return
      }
      const focusable = [...panel.current.querySelectorAll<HTMLElement>(FOCUSABLE)]
      if (focusable.length === 0) {
        return
      }
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      previouslyFocused?.focus()
    }
  }, [open])

  if (!open) {
    return null
  }
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div aria-hidden="true" className="absolute inset-0 bg-black/70" onClick={onClose} />
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="relative w-full max-w-md space-y-4 rounded-card border border-line bg-surface p-6 shadow-xl"
      >
        <h2 id={titleId} className="text-lg font-semibold">
          {title}
        </h2>
        <div className="space-y-3 text-sm text-ink-muted">{children}</div>
        <div className="flex flex-wrap justify-end gap-2">{actions}</div>
      </div>
    </div>,
    document.body,
  )
}
