import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { apiGet } from '../../lib/api'
import type { Stats, StatsRange } from './types'

export function useStats(range: StatsRange) {
  return useQuery({
    // Tracking and library changes invalidate ['stats'].
    queryKey: ['stats', range],
    queryFn: ({ signal }) => apiGet<Stats>(`/api/v1/me/stats?range=${range}`, { signal }),
    placeholderData: keepPreviousData,
  })
}

/** "10 h 15 min", "45 min", "0 min". */
export function formatMinutes(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours === 0) {
    return `${rest} min`
  }
  return rest === 0 ? `${hours.toLocaleString()} h` : `${hours.toLocaleString()} h ${rest} min`
}
