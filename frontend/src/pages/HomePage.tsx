import { CircleAlert, CircleCheck, LoaderCircle } from 'lucide-react'
import type { ReactNode } from 'react'

import { useBackendHealth } from '../features/system/useBackendHealth'

export function HomePage() {
  return (
    <section className="max-w-2xl space-y-6">
      <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Track every episode.</h1>
      <p className="text-ink-muted">
        Watchmark is being built milestone by milestone. This page currently only verifies that the
        frontend can reach the backend.
      </p>
      <BackendStatus />
    </section>
  )
}

function BackendStatus() {
  const health = useBackendHealth()

  if (health.isPending) {
    return (
      <StatusCard icon={<LoaderCircle aria-hidden="true" className="size-5 animate-spin text-ink-muted" />}>
        Checking backend…
      </StatusCard>
    )
  }

  if (health.isError || health.data.status !== 'UP') {
    return (
      <StatusCard icon={<CircleAlert aria-hidden="true" className="size-5 text-danger" />}>
        Backend unavailable. Start it with <code className="text-ink">./mvnw spring-boot:run</code> in{' '}
        <code className="text-ink">backend/</code>.
      </StatusCard>
    )
  }

  return (
    <StatusCard icon={<CircleCheck aria-hidden="true" className="size-5 text-success" />}>
      Backend is up and connected to its database.
    </StatusCard>
  )
}

function StatusCard({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <div role="status" className="flex items-center gap-3 rounded-card border border-line bg-surface p-4">
      {icon}
      <p>{children}</p>
    </div>
  )
}
