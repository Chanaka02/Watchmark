import { keepPreviousData, useQuery } from '@tanstack/react-query'

import { apiGet } from '../../lib/api'
import type { Calendar } from './types'

/** Episodes from `from` to `to` (inclusive, at most 62 days). */
export function useCalendar(from: string, to: string) {
  return useQuery({
    // Tracking and library changes invalidate ['calendar'] (watched state, followed shows).
    queryKey: ['calendar', from, to],
    queryFn: ({ signal }) => apiGet<Calendar>(`/api/v1/me/calendar?${new URLSearchParams({ from, to })}`, { signal }),
    // Keep the current range on screen while the next one loads.
    placeholderData: keepPreviousData,
  })
}
