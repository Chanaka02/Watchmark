import { Clapperboard, LogOut, Search } from 'lucide-react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router'

import { Button } from '../components/Button'
import { buttonClasses } from '../components/buttonStyles'
import { useCurrentUser, useLogout } from '../features/auth/useAuth'
import { Attribution } from '../features/catalog/Attribution'

export function AppLayout() {
  const currentUser = useCurrentUser()

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
          <div className="flex items-center gap-6">
            <Link to="/" className="flex items-center gap-2 rounded-md text-lg font-semibold tracking-tight">
              <Clapperboard aria-hidden="true" className="size-6 text-accent" />
              Watchmark
            </Link>
            {currentUser.data && <MainNav />}
          </div>
          <AccountNav />
        </div>
      </header>

      <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 py-10 sm:px-6">
        <Outlet />
      </main>

      <footer className="space-y-3 border-t border-line py-6 text-center text-sm text-ink-muted">
        <Attribution />
        <p>Watchmark is a personal, non-commercial project.</p>
      </footer>
    </div>
  )
}

function MainNav() {
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `rounded-md px-2 py-1 text-sm font-medium ${isActive ? 'text-ink' : 'text-ink-muted hover:text-ink'}`
  return (
    <nav aria-label="Main" className="flex items-center gap-2">
      <NavLink to="/discover" className={linkClass}>
        Discover
      </NavLink>
      <NavLink to="/search" className={linkClass}>
        <span className="flex items-center gap-1">
          <Search aria-hidden="true" className="size-4" />
          Search
        </span>
      </NavLink>
    </nav>
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
