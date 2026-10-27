import type { ReactNode } from 'react'
import type { RouteObject } from 'react-router'

import { RedirectIfSignedIn } from '../features/auth/RedirectIfSignedIn'
import { RequireAuth } from '../features/auth/RequireAuth'
import { DiscoverPage } from '../pages/DiscoverPage'
import { HomePage } from '../pages/HomePage'
import { LoginPage } from '../pages/LoginPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { RegisterPage } from '../pages/RegisterPage'
import { SearchPage } from '../pages/SearchPage'
import { SeasonPage } from '../pages/SeasonPage'
import { ShowPage } from '../pages/ShowPage'
import { AppLayout } from './AppLayout'

const signedIn = (page: ReactNode) => <RequireAuth>{page}</RequireAuth>
const signedOut = (page: ReactNode) => <RedirectIfSignedIn>{page}</RedirectIfSignedIn>

// Route objects are shared by the browser router and memory routers in tests.
export const routes: RouteObject[] = [
  {
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'login', element: signedOut(<LoginPage />) },
      { path: 'register', element: signedOut(<RegisterPage />) },
      { path: 'discover', element: signedIn(<DiscoverPage />) },
      { path: 'search', element: signedIn(<SearchPage />) },
      { path: 'shows/:ref', element: signedIn(<ShowPage />) },
      { path: 'shows/:ref/seasons/:seasonNumber', element: signedIn(<SeasonPage />) },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
