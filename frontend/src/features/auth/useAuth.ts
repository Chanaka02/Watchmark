import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'

import { fetchCurrentUser, login, logout, register } from './api'

export const currentUserKey = ['auth', 'me'] as const

export function useCurrentUser() {
  return useQuery({
    queryKey: currentUserKey,
    queryFn: ({ signal }) => fetchCurrentUser(signal),
    staleTime: 5 * 60 * 1000,
    retry: false,
  })
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: login,
    onSuccess: (user) => queryClient.setQueryData(currentUserKey, user),
  })
}

export function useRegister() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: register,
    onSuccess: (user) => queryClient.setQueryData(currentUserKey, user),
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: logout,
    onSettled: () => showSignedOut(queryClient),
  })
}

/**
 * After sign-out or account deletion. "Signed out" is set first so every
 * mounted component sees it (clearing the cache first would leave them
 * watching removed entries, still showing the old user's pages); then
 * everything else cached is dropped, so nothing from that account can leak
 * into the next session on this device.
 */
export function showSignedOut(queryClient: QueryClient) {
  queryClient.setQueryData(currentUserKey, null)
  queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== currentUserKey[0] })
}
