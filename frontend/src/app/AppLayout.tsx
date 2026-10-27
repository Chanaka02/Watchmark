import { Clapperboard, LogOut } from 'lucide-react'
import { Link, Outlet, useNavigate } from 'react-router'

import { Button } from '../components/Button'
import { buttonClasses } from '../components/buttonStyles'
import { useCurrentUser, useLogout } from '../features/auth/useAuth'

export function AppLayout() {
  return (
    <div className="flex min-h-dvh flex-col">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:rounded-md focus:bg-surface-raised focus:px-3 focus:py-2"
      >
        Skip to content
      </a>

      <header className="border-b border-line bg-surface/80">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
          <Link to="/" className="flex items-center gap-2 rounded-md text-lg font-semibold tracking-tight">
            <Clapperboard aria-hidden="true" className="size-6 text-accent" />
            Watchmark
          </Link>
          <AccountNav />
        </div>
      </header>

      <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 py-10 sm:px-6">
        <Outlet />
      </main>

      {/* The TMDB logo and required notice are added in Milestone 2, when TMDB data is first shown. */}
      <footer className="border-t border-line py-6 text-center text-sm text-ink-muted">
        Watchmark is a personal, non-commercial project.
      </footer>
    </div>
  )
}

function AccountNav() {
  const navigate = useNavigate()
  const currentUser = useCurrentUser()
  const logout = useLogout()

  if (currentUser.isPending || currentUser.isError) {
    return null
  }
  if (!currentUser.data) {
    return (
      <nav aria-label="Account" className="flex items-center gap-2">
        <Link to="/login" className={buttonClasses('ghost')}>
          Sign in
        </Link>
        <Link to="/register" className={buttonClasses('primary')}>
          Create account
        </Link>
      </nav>
    )
  }
  return (
    <nav aria-label="Account" className="flex items-center gap-3">
      <span className="hidden text-sm text-ink-muted sm:inline">{currentUser.data.displayName}</span>
      <Button variant="ghost" pending={logout.isPending} onClick={() => logout.mutate(undefined, { onSettled: () => navigate('/') })}>
        <LogOut aria-hidden="true" className="size-4" />
        Sign out
      </Button>
    </nav>
  )
}
