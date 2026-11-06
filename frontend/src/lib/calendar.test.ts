import { describe, expect, it } from 'vitest'

import { addMonths, formatMonth, isMonth, monthGrid, weekdayIndex, weekdayNames } from './calendar'

describe('monthGrid', () => {
  it('covers the month with whole Monday-first weeks', () => {
    const weeks = monthGrid('2026-11')
    // 1 November 2026 is a Sunday, 30 November a Monday.
    expect(weeks[0][0]).toBe('2026-10-26')
    expect(weeks[0][6]).toBe('2026-11-01')
    expect(weeks.at(-1)).toEqual(['2026-11-30', '2026-12-01', '2026-12-02', '2026-12-03', '2026-12-04', '2026-12-05', '2026-12-06'])
    expect(weeks).toHaveLength(6)
    expect(weeks.every((week) => week.length === 7)).toBe(true)
  })

  it('needs only four weeks for a February that starts on Monday', () => {
    expect(monthGrid('2027-02')).toHaveLength(4)
  })
})

describe('month helpers', () => {
  it('moves across years', () => {
    expect(addMonths('2026-12', 1)).toBe('2027-01')
    expect(addMonths('2026-01', -1)).toBe('2025-12')
  })

  it('validates and formats months', () => {
    expect(isMonth('2026-11')).toBe(true)
    expect(isMonth('2026-13')).toBe(false)
    expect(isMonth('nov')).toBe(false)
    expect(formatMonth('2026-11', 'en-GB')).toBe('November 2026')
  })

  it('numbers weekdays from Monday', () => {
    expect(weekdayIndex('2026-11-02')).toBe(0)
    expect(weekdayIndex('2026-11-01')).toBe(6)
    expect(weekdayNames('en-GB')).toEqual(['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'])
  })
})
