import type { ReactNode } from 'react'
import { Navigate, useSearchParams } from 'react-router'

import { PageLoader } from '../../components/PageLoader'
import { safeNextPath } from './redirects'
import { useCurrentUser } from './useAuth'

/** Login and registration pages are pointless when already signed in. */
export function RedirectIfSignedIn({ children }: { children: ReactNode }) {
  const [params] = useSearchParams()
  const currentUser = useCurrentUser()

  if (currentUser.isPending) {
    return <PageLoader />
  }
  if (currentUser.data) {
    return <Navigate to={safeNextPath(params.get('next'))} replace />
  }
  // On an error, still show the form: signing in may well fix it.
  return children
}
