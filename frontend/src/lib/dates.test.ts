import { describe, expect, it } from 'vitest'

import { airStatus, formatDate, todayIn, yearOf } from './dates'

describe('todayIn', () => {
  // 2026-10-27 23:30 UTC: already the 28th in Colombo, still the 27th in Los Angeles.
  const instant = new Date('2026-10-27T23:30:00Z')

  it('uses the given time zone, not the machine one', () => {
    expect(todayIn('Asia/Colombo', instant)).toBe('2026-10-28')
    expect(todayIn('America/Los_Angeles', instant)).toBe('2026-10-27')
    expect(todayIn('UTC', instant)).toBe('2026-10-27')
  })
})

describe('airStatus', () => {
  it('classifies relative to today', () => {
    expect(airStatus('2026-10-27', '2026-10-28')).toBe('aired')
    expect(airStatus('2026-10-28', '2026-10-28')).toBe('today')
    expect(airStatus('2026-10-29', '2026-10-28')).toBe('upcoming')
  })

  it('treats missing or malformed dates as unknown, never as aired', () => {
    expect(airStatus(null, '2026-10-28')).toBe('unknown')
    expect(airStatus('', '2026-10-28')).toBe('unknown')
    expect(airStatus('soon', '2026-10-28')).toBe('unknown')
  })
})

describe('formatDate', () => {
  it('formats a calendar date without shifting it by time zone', () => {
    expect(formatDate('2021-03-01', 'en-GB')).toBe('1 Mar 2021')
    expect(formatDate('2021-12-31', 'en-GB')).toBe('31 Dec 2021')
  })

  it('returns null for missing dates', () => {
    expect(formatDate(null)).toBeNull()
    expect(formatDate('2021-3-1')).toBeNull()
  })
})

describe('yearOf', () => {
  it('extracts the year', () => {
    expect(yearOf('2021-03-14')).toBe('2021')
    expect(yearOf(null)).toBeNull()
  })
})
