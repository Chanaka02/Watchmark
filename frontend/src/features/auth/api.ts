import { ApiError, apiGet, apiPost } from '../../lib/api'

/** Mirrors backend `UserResponse`. */
export type User = {
  id: number
  email: string
  displayName: string
  timeZone: string
}

export type LoginInput = { email: string; password: string }

export type RegisterInput = LoginInput & { displayName: string; timeZone: string }

/** The signed-in user, or `null` when there is no valid session. */
export async function fetchCurrentUser(signal?: AbortSignal): Promise<User | null> {
  try {
    return await apiGet<User>('/api/v1/auth/me', { signal })
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

export function login(input: LoginInput) {
  return apiPost<User>('/api/v1/auth/login', { body: input })
}

export function register(input: RegisterInput) {
  return apiPost<User>('/api/v1/auth/register', { body: input })
}

export function logout() {
  return apiPost<void>('/api/v1/auth/logout')
}
