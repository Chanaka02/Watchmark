import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

export function EmptyState({ icon: Icon, title, children }: { icon: LucideIcon; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-card border border-dashed border-line px-6 py-14 text-center">
      <Icon aria-hidden="true" className="size-8 text-ink-muted" />
      <p className="font-medium">{title}</p>
      {children && <div className="max-w-md text-sm text-ink-muted">{children}</div>}
    </div>
  )
}
