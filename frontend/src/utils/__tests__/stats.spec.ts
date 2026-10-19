import { describe, expect, it } from 'vitest'

import { fromOpeningHours, toOpeningHours } from '@/validation/place'

import { deltaText, shiftDate, splitPeriods, sum, vietnamToday } from '../stats'

const day = (date: string, count: number) => ({ date, count })

describe('thống kê chủ địa điểm (P09)', () => {
  it('hôm nay theo giờ Việt Nam — 18:00 UTC đã sang ngày mới ở VN', () => {
    expect(vietnamToday(new Date('2026-10-18T16:59:00Z'))).toBe('2026-10-18')
    expect(vietnamToday(new Date('2026-10-18T17:00:00Z'))).toBe('2026-10-19')
  })

  it('lùi ngày theo lịch, qua tháng / năm', () => {
    expect(shiftDate('2026-10-18', -59)).toBe('2026-08-20')
    expect(shiftDate('2026-01-01', -1)).toBe('2025-12-31')
  })

  it('tách kỳ này / kỳ trước cùng độ dài từ một chuỗi 2n ngày', () => {
    const series = [day('d1', 1), day('d2', 2), day('d3', 3), day('d4', 4)]
    const { current, previous } = splitPeriods(series, 2)
    expect(current.map((d) => d.date)).toEqual(['d3', 'd4'])
    expect(previous.map((d) => d.date)).toEqual(['d1', 'd2'])
    expect(sum(current)).toBe(7)
  })

  it('thay đổi so với kỳ trước: mũi tên + chữ, không chia cho 0', () => {
    expect(deltaText(110, 100)).toEqual({ text: '▲ 10%', trend: 'up' })
    expect(deltaText(97, 100)).toEqual({ text: '▼ 3%', trend: 'down' })
    expect(deltaText(1092, 1000)).toEqual({ text: '▲ 9,2%', trend: 'up' })
    expect(deltaText(5, 5)).toEqual({ text: 'Không đổi', trend: 'flat' })
    expect(deltaText(12, 0)).toEqual({ text: '▲ 12', trend: 'up' })
  })
})

describe('giờ mở cửa API → form (màn sửa của chủ)', () => {
  it('ngày vắng = đóng cửa, bỏ giây, ngày nhiều khung lấy khung sớm nhất', () => {
    const week = fromOpeningHours([
      { dayOfWeek: 1, openTime: '07:00:00', closeTime: '22:00:00' },
      { dayOfWeek: 6, openTime: '18:00', closeTime: '02:00' },
      { dayOfWeek: 6, openTime: '06:00', closeTime: '10:00' },
    ])
    expect(week[0]).toEqual({ open: true, openTime: '07:00', closeTime: '22:00' })
    expect(week[1]!.open).toBe(false)
    expect(week[5]).toEqual({ open: true, openTime: '06:00', closeTime: '10:00' })
    expect(toOpeningHours(week)).toEqual([
      { dayOfWeek: 1, openTime: '07:00', closeTime: '22:00' },
      { dayOfWeek: 6, openTime: '06:00', closeTime: '10:00' },
    ])
  })
})
