import type { RouteObject } from 'react-router'

import { RedirectIfSignedIn } from '../features/auth/RedirectIfSignedIn'
import { HomePage } from '../pages/HomePage'
import { LoginPage } from '../pages/LoginPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { RegisterPage } from '../pages/RegisterPage'
import { AppLayout } from './AppLayout'

// Route objects are shared by the browser router and memory routers in tests.
// Pages that need a signed-in user wrap their element in <RequireAuth>.
export const routes: RouteObject[] = [
  {
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      {
        path: 'login',
        element: (
          <RedirectIfSignedIn>
            <LoginPage />
          </RedirectIfSignedIn>
        ),
      },
      {
        path: 'register',
        element: (
          <RedirectIfSignedIn>
            <RegisterPage />
          </RedirectIfSignedIn>
        ),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
