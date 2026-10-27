import type { ReactNode } from 'react'

/** Shared frame for the sign-in and registration forms. */
export function AuthCard({ title, children, footer }: { title: string; children: ReactNode; footer: ReactNode }) {
  return (
    <section className="mx-auto w-full max-w-md space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
      <div className="rounded-card border border-line bg-surface p-6">{children}</div>
      <p className="text-center text-sm text-ink-muted">{footer}</p>
    </section>
  )
}
