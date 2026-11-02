import { useQuery } from '@tanstack/react-query'

import { apiGet } from '../../lib/api'
import type { Dashboard } from './types'

export const dashboardKey = ['dashboard'] as const

export function useDashboard() {
  return useQuery({
    queryKey: dashboardKey,
    queryFn: ({ signal }) => apiGet<Dashboard>('/api/v1/me/dashboard', { signal }),
  })
}
