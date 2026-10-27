import { Link } from 'react-router'

import { buttonClasses } from '../components/buttonStyles'
import { ErrorMessage } from '../components/ErrorMessage'
import { PageLoader } from '../components/PageLoader'
import { useCurrentUser } from '../features/auth/useAuth'

export function HomePage() {
  const currentUser = useCurrentUser()

  if (currentUser.isPending) {
    return <PageLoader />
  }
  if (currentUser.isError) {
    return <ErrorMessage title="Watchmark is unavailable right now">{currentUser.error.message}</ErrorMessage>
  }
  if (currentUser.data) {
    return (
      <section className="max-w-2xl space-y-4">
        <h1 className="text-3xl font-bold tracking-tight">Welcome back, {currentUser.data.displayName}.</h1>
        <p className="text-ink-muted">
          Your dashboard with shows in progress and upcoming episodes is coming in a later milestone.
        </p>
      </section>
    )
  }
  return (
    <section className="max-w-2xl space-y-6">
      <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Track every episode.</h1>
      <p className="text-lg text-ink-muted">
        Keep a personal library of the series you watch, mark episodes as you go, and always know what to watch
        next.
      </p>
      <div className="flex flex-wrap gap-3">
        <Link to="/register" className={buttonClasses('primary')}>
          Create an account
        </Link>
        <Link to="/login" className={buttonClasses('secondary')}>
          Sign in
        </Link>
      </div>
    </section>
  )
}
