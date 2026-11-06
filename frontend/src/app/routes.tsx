import { lazy, type ComponentType, type ReactNode } from 'react'
import type { RouteObject } from 'react-router'

import { RedirectIfSignedIn } from '../features/auth/RedirectIfSignedIn'
import { RequireAuth } from '../features/auth/RequireAuth'
import { HomePage } from '../pages/HomePage'
import { LoginPage } from '../pages/LoginPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { RegisterPage } from '../pages/RegisterPage'
import { RouteErrorPage } from '../pages/RouteErrorPage'
import { AppLayout } from './AppLayout'

/**
 * Pages behind sign-in load on first visit (the layout shows a loader meanwhile);
 * the landing, sign-in and register pages stay in the main bundle so a first
 * visit needs one download.
 */
function page<K extends string>(load: () => Promise<Record<K, ComponentType>>, name: K) {
  return lazy(() => load().then((module) => ({ default: module[name] })))
}

const CalendarPage = page(() => import('../pages/CalendarPage'), 'CalendarPage')
const DiscoverPage = page(() => import('../pages/DiscoverPage'), 'DiscoverPage')
const HistoryPage = page(() => import('../pages/HistoryPage'), 'HistoryPage')
const LibraryPage = page(() => import('../pages/LibraryPage'), 'LibraryPage')
const SearchPage = page(() => import('../pages/SearchPage'), 'SearchPage')
const SeasonPage = page(() => import('../pages/SeasonPage'), 'SeasonPage')
const SettingsPage = page(() => import('../pages/SettingsPage'), 'SettingsPage')
const ShowPage = page(() => import('../pages/ShowPage'), 'ShowPage')
const StatsPage = page(() => import('../pages/StatsPage'), 'StatsPage')

const signedIn = (element: ReactNode) => <RequireAuth>{element}</RequireAuth>
const signedOut = (element: ReactNode) => <RedirectIfSignedIn>{element}</RedirectIfSignedIn>

// Route objects are shared by the browser router and memory routers in tests.
export const routes: RouteObject[] = [
  {
    element: <AppLayout />,
    // Only reached if the layout itself fails; page errors are caught below, inside it.
    errorElement: <RouteErrorPage />,
    children: [
      {
        errorElement: <RouteErrorPage />,
        children: [
          { index: true, element: <HomePage /> },
          { path: 'login', element: signedOut(<LoginPage />) },
          { path: 'register', element: signedOut(<RegisterPage />) },
          { path: 'discover', element: signedIn(<DiscoverPage />) },
          { path: 'library', element: signedIn(<LibraryPage />) },
          { path: 'history', element: signedIn(<HistoryPage />) },
          { path: 'calendar', element: signedIn(<CalendarPage />) },
          { path: 'stats', element: signedIn(<StatsPage />) },
          { path: 'settings', element: signedIn(<SettingsPage />) },
          { path: 'search', element: signedIn(<SearchPage />) },
          { path: 'shows/:ref', element: signedIn(<ShowPage />) },
          { path: 'shows/:ref/seasons/:seasonNumber', element: signedIn(<SeasonPage />) },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]
