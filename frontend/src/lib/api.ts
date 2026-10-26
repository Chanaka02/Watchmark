/**
 * Minimal same-origin JSON client. Every feature calls the backend through
 * this function so credentials, error mapping and (from Milestone 1) the CSRF
 * header are handled in one place.
 */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function apiGet<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      credentials: 'same-origin',
      headers: { Accept: 'application/json', ...init?.headers },
    })
  } catch {
    throw new ApiError(0, 'The server could not be reached.')
  }

  if (!response.ok) {
    throw new ApiError(response.status, `Request failed with status ${response.status}.`)
  }
  return (await response.json()) as T
}
