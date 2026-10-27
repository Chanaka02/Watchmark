import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

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
    onSettled: () => {
      // Drop every cached query: nothing from the previous user may leak into
      // the next session on this device.
      queryClient.clear()
      queryClient.setQueryData(currentUserKey, null)
    },
  })
}
