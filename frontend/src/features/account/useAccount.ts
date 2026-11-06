import { useMutation, useQueryClient } from '@tanstack/react-query'

import { apiDelete, apiPatch, apiPut } from '../../lib/api'
import type { User } from '../auth/api'
import { currentUserKey, showSignedOut } from '../auth/useAuth'

export function useUpdateProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { displayName: string; timeZone: string }) =>
      apiPatch<User>('/api/v1/me/profile', { body: input }),
    onSuccess: (user) => {
      queryClient.setQueryData(currentUserKey, user)
      // "Today" depends on the time zone: everything date-based must be refetched.
      void queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] !== currentUserKey[0] })
    },
  })
}

/** Other sessions are signed out by the server; this one stays signed in. */
export function useChangePassword() {
  return useMutation({
    mutationFn: (input: { currentPassword: string; newPassword: string }) =>
      apiPut<void>('/api/v1/me/password', { body: input }),
  })
}

/** Permanently deletes the account and all personal data; every session ends. */
export function useDeleteAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (password: string) => apiDelete<void>('/api/v1/me', { body: { password } }),
    onSuccess: () => showSignedOut(queryClient),
  })
}
