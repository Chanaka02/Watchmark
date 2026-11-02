import { describe, expect, it } from 'vitest'

import { addDays, airStatus, dateIn, formatDate, relativeDay, timeIn, todayIn, yearOf } from './dates'

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

describe('dateIn', () => {
  it("puts an instant on the user's calendar day", () => {
    // 22:30 UTC on 1 November is already 2 November in Colombo (UTC+5:30).
    expect(dateIn('2026-11-01T22:30:00Z', 'Asia/Colombo')).toBe('2026-11-02')
    expect(dateIn('2026-11-01T22:30:00Z', 'America/Los_Angeles')).toBe('2026-11-01')
  })
})

describe('addDays and relativeDay', () => {
  it('crosses month and year boundaries', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
  })

  it('names nearby days and formats the rest', () => {
    expect(relativeDay('2026-11-02', '2026-11-02')).toBe('Today')
    expect(relativeDay('2026-11-03', '2026-11-02')).toBe('Tomorrow')
    expect(relativeDay('2026-11-01', '2026-11-02')).toBe('Yesterday')
    expect(relativeDay('2026-11-10', '2026-11-02', 'en-GB')).toBe('10 Nov 2026')
  })
})

describe('timeIn', () => {
  it("shows the time in the user's zone", () => {
    expect(timeIn('2026-11-01T22:30:00Z', 'Asia/Colombo', 'en-GB')).toBe('04:00')
  })
})
