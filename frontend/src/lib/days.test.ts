import { describe, expect, it } from 'vitest'
import { formatTime, quickRangeDays, todayInSydney } from './days'

/** The days of the time filter and the times of the logs, in Sydney (The time filter in page-design.md). */

describe('today', () => {
  it('is the day in Sydney, not in UTC', () => {
    expect(todayInSydney(new Date('2026-11-15T14:00:00Z'))).toBe('2026-11-16')
    expect(todayInSydney(new Date('2026-11-15T12:59:00Z'))).toBe('2026-11-15')
  })
})

describe('the quick ranges', () => {
  it('count N days including today', () => {
    expect(quickRangeDays('days1', '2026-11-15')).toEqual({ from: '2026-11-15', to: '2026-11-15' })
    expect(quickRangeDays('days3', '2026-11-15')).toEqual({ from: '2026-11-13', to: '2026-11-15' })
    expect(quickRangeDays('days15', '2026-03-05')).toEqual({ from: '2026-02-19', to: '2026-03-05' })
  })

  it('start one or three months back plus one day', () => {
    expect(quickRangeDays('months1', '2026-11-15')).toEqual({ from: '2026-10-16', to: '2026-11-15' })
    expect(quickRangeDays('months3', '2026-01-10')).toEqual({ from: '2025-10-11', to: '2026-01-10' })
  })

  it('take the last day of a shorter month', () => {
    expect(quickRangeDays('months1', '2026-03-31')).toEqual({ from: '2026-03-01', to: '2026-03-31' })
    expect(quickRangeDays('months3', '2026-05-31')).toEqual({ from: '2026-03-01', to: '2026-05-31' })
  })
})

describe('a time', () => {
  it('is shown in Sydney time, in summer and in winter', () => {
    expect(formatTime('2026-11-15T04:58:00Z')).toBe('15 Nov 2026 15:58')
    expect(formatTime('2026-07-01T00:05:00Z')).toBe('1 Jul 2026 10:05')
  })
})
