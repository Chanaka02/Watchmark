import { LoaderCircle } from 'lucide-react'

export function PageLoader({ label = 'Loading…' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center gap-3 py-16 text-ink-muted">
      <LoaderCircle aria-hidden="true" className="size-5 animate-spin" />
      <span>{label}</span>
    </div>
  )
}
