import { browserTimeZone } from '../auth/schemas'
import { useCurrentUser } from '../auth/useAuth'
import { todayIn } from '../../lib/dates'

/** Today's date (`YYYY-MM-DD`) in the signed-in user's time zone, falling back to the browser's. */
export function useToday(): { today: string; timeZone: string } {
  const currentUser = useCurrentUser()
  const timeZone = currentUser.data?.timeZone ?? browserTimeZone()
  return { today: todayIn(timeZone), timeZone }
}
