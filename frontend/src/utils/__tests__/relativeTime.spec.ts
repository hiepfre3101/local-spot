import { describe, expect, it } from 'vitest'

import { relativeTime } from '../format'

const now = new Date('2026-10-19T15:00:00').getTime()

describe('relativeTime', () => {
  it.each([
    ['2026-10-19T14:59:40', 'Vừa xong'],
    ['2026-10-19T14:55:00', '5 phút trước'],
    ['2026-10-19T12:00:00', '3 giờ trước'],
    ['2026-10-18T23:00:00', 'Hôm qua'],
    ['2026-10-18T08:00:00', 'Hôm qua'],
    ['2026-10-17T20:00:00', '2 ngày trước'],
    ['2026-10-10T09:00:00', '10/10/2026'],
  ])('%s → %s', (iso, expected) => {
    expect(relativeTime(iso, now)).toBe(expected)
  })
})
