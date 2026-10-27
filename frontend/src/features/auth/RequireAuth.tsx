import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'

import { ErrorMessage } from '../../components/ErrorMessage'
import { PageLoader } from '../../components/PageLoader'
import { loginPathFor } from './redirects'
import { useCurrentUser } from './useAuth'

/** Renders `children` only for a signed-in user; otherwise redirects to login. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation()
  const currentUser = useCurrentUser()

  if (currentUser.isPending) {
    return <PageLoader />
  }
  if (currentUser.isError) {
    return <ErrorMessage title="Could not check your session">{currentUser.error.message}</ErrorMessage>
  }
  if (currentUser.data === null) {
    return <Navigate to={loginPathFor(location.pathname + location.search)} replace />
  }
  return children
}
