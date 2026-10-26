import { useQuery } from '@tanstack/react-query'

import { apiGet } from '../../lib/api'

type HealthResponse = { status: string }

export function useBackendHealth() {
  return useQuery({
    queryKey: ['system', 'health'],
    queryFn: ({ signal }) => apiGet<HealthResponse>('/actuator/health', { signal }),
    retry: false,
  })
}
