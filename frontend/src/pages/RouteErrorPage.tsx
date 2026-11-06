import { isRouteErrorResponse, Link, useRouteError } from 'react-router'

import { Button } from '../components/Button'
import { NotFoundPage } from './NotFoundPage'

/**
 * Shown instead of a page that failed to render or load. It sits inside the
 * layout, so the header and navigation keep working. Error details go to the
 * console for debugging, never onto the page.
 */
export function RouteErrorPage() {
  const error = useRouteError()

  if (isRouteErrorResponse(error) && error.status === 404) {
    return <NotFoundPage />
  }
  console.error(error)
  // A lazily loaded page whose file no longer exists usually means a new version was deployed.
  const staleVersion = error instanceof Error && /dynamically imported module|Importing a module script failed/i.test(error.message)

  return (
    <section role="alert" className="max-w-xl space-y-4">
      <h1 className="text-2xl font-bold">Something went wrong</h1>
      <p className="text-ink-muted">
        {staleVersion
          ? 'Watchmark was updated while this page was open. Reload to get the latest version.'
          : 'This page could not be shown. Reloading usually helps; your data is safe.'}
      </p>
      <div className="flex flex-wrap gap-3">
        <Button onClick={() => window.location.reload()}>Reload</Button>
        <Link to="/" className="inline-flex h-10 items-center text-accent underline-offset-4 hover:underline">
          Back to home
        </Link>
      </div>
    </section>
  )
}
