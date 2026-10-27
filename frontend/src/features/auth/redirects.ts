/**
 * Returns `next` only if it is a same-site path such as `/library?tab=2`.
 * Anything else (absolute URLs, `//evil.example`, `/\evil.example`) falls back
 * to `/`, so the login page cannot be used as an open redirect.
 */
export function safeNextPath(next: string | null | undefined): string {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) {
    return '/'
  }
  return next
}

export function loginPathFor(currentPath: string): string {
  return currentPath === '/' ? '/login' : `/login?next=${encodeURIComponent(currentPath)}`
}
