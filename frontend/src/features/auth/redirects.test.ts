import { describe, expect, it } from 'vitest'

import { loginPathFor, safeNextPath } from './redirects'

describe('safeNextPath', () => {
  it.each([
    ['/library', '/library'],
    ['/shows/tmdb:1399?season=2', '/shows/tmdb:1399?season=2'],
    [null, '/'],
    ['', '/'],
    ['https://evil.example', '/'],
    ['//evil.example', '/'],
    ['/\\evil.example', '/'],
    ['javascript:alert(1)', '/'],
  ])('%s -> %s', (input, expected) => {
    expect(safeNextPath(input)).toBe(expected)
  })
})

describe('loginPathFor', () => {
  it('omits next for the home page and encodes other paths', () => {
    expect(loginPathFor('/')).toBe('/login')
    expect(loginPathFor('/library?status=WATCHING')).toBe('/login?next=%2Flibrary%3Fstatus%3DWATCHING')
  })
})
