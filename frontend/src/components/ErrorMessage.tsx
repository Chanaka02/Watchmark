import { CircleAlert } from 'lucide-react'
import type { ReactNode } from 'react'

import { Button } from './Button'

type ErrorMessageProps = {
  title: string
  children?: ReactNode
  /** Shows a "Try again" button. */
  onRetry?: () => void
}

export function ErrorMessage({ title, children, onRetry }: ErrorMessageProps) {
  return (
    <div role="alert" className="flex gap-3 rounded-card border border-danger/40 bg-danger/10 p-4">
      <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-danger" />
      <div className="space-y-2">
        <p className="font-medium">{title}</p>
        {children && <div className="text-sm text-ink-muted">{children}</div>}
        {onRetry && (
          <Button variant="secondary" onClick={onRetry}>
            Try again
          </Button>
        )}
      </div>
    </div>
  )
}
