import { describe, expect, it } from 'vitest'

import { formatPriceRange, tagLabel } from '../format'
import { distanceM, formatDistance, radiusForView } from '../geo'
import { openState, weekSchedule } from '../openingHours'
import { discFills, ratingLabel, scoreText } from '../rating'

describe('Rating — 5 hình tròn tô theo tỷ lệ', () => {
  it('4.7 → 4 hình đầy + 70% hình thứ năm (README Rating)', () => {
    expect(discFills(4.7)).toEqual([100, 100, 100, 100, 70])
    expect(discFills(2.2)).toEqual([100, 100, 20, 0, 0])
    expect(discFills(0.8)).toEqual([80, 0, 0, 0, 0])
  })

  it('làm tròn phần lẻ tới 10%; kẹp trong 0–5', () => {
    expect(discFills(4.349)).toEqual([100, 100, 100, 100, 30])
    expect(discFills(4.35)).toEqual([100, 100, 100, 100, 40])
    expect(discFills(7)).toEqual([100, 100, 100, 100, 100])
    expect(discFills(-1)).toEqual([0, 0, 0, 0, 0])
  })

  it('số hiển thị và nhãn đọc màn hình theo kiểu Việt Nam', () => {
    expect(scoreText(4.123)).toBe('4.1')
    expect(scoreText(5)).toBe('5.0')
    expect(ratingLabel(4.7, 2314)).toBe('4,7 trên 5 điểm, 2.314 đánh giá')
    expect(ratingLabel(4)).toBe('4,0 trên 5 điểm')
  })
})

describe('Khoảng cách', () => {
  it('haversine khớp ST_Distance_Sphere (≈ 111,2 km mỗi độ vĩ)', () => {
    expect(distanceM({ lat: 21, lng: 105 }, { lat: 22, lng: 105 })).toBeCloseTo(111_195, -1)
  })

  it('"Tìm trong vùng này": bán kính tới cạnh gần hơn, làm tròn lên 100 m, kẹp 100 m – 20 km', () => {
    const center = { lat: 21.0285, lng: 105.8542 }
    // Khung cao ~2 km, rộng ~4 km → lấy nửa chiều cao 990 m, làm tròn lên 1 km
    const north = center.lat + 990 / 111_195
    const east = center.lng + 2000 / (111_195 * Math.cos((center.lat * Math.PI) / 180))
    expect(radiusForView(center, north, east)).toBe(1000)
    expect(radiusForView(center, center.lat + 0.0001, center.lng + 0.0001)).toBe(100)
    expect(radiusForView(center, center.lat + 5, center.lng + 5)).toBe(20_000)
  })

  it('định dạng mét / km', () => {
    expect(formatDistance(347)).toBe('350 m')
    expect(formatDistance(2)).toBe('1 m')
    expect(formatDistance(1234)).toBe('1,2 km')
    expect(formatDistance(2000)).toBe('2 km')
    expect(formatDistance(16_400)).toBe('16 km')
  })
})

describe('Giờ mở cửa — theo giờ Việt Nam', () => {
  const hours = [
    { dayOfWeek: 1, openTime: '06:00', closeTime: '10:30' },
    { dayOfWeek: 1, openTime: '17:00', closeTime: '21:00' },
    { dayOfWeek: 6, openTime: '18:00', closeTime: '02:00' },
  ]
  /** Giờ VN = UTC+7: 2026-10-12 là thứ Hai. */
  const vn = (iso: string) => new Date(`${iso}+07:00`)

  it('đang mở / đã đóng trong ngày, nhiều khung giờ', () => {
    expect(openState(hours, vn('2026-10-12T07:00:00'))).toEqual({
      open: true,
      today: '06:00 – 10:30, 17:00 – 21:00',
    })
    expect(openState(hours, vn('2026-10-12T12:00:00'))?.open).toBe(false)
    expect(openState(hours, vn('2026-10-12T21:00:00'))?.open).toBe(false)
  })

  it('khung qua đêm còn hiệu lực tới giờ đóng sáng hôm sau', () => {
    expect(openState(hours, vn('2026-10-17T23:30:00'))?.open).toBe(true) // thứ Bảy
    expect(openState(hours, vn('2026-10-18T01:30:00'))).toEqual({ open: true, today: '' }) // Chủ nhật
    expect(openState(hours, vn('2026-10-18T02:00:00'))?.open).toBe(false)
  })

  it('dùng giờ Việt Nam bất kể múi giờ máy xem', () => {
    // 2026-10-12T00:30Z = 07:30 thứ Hai ở Việt Nam
    expect(openState(hours, new Date('2026-10-12T00:30:00Z'))?.open).toBe(true)
  })

  it('chưa khai giờ → null; lịch tuần đủ 7 ngày', () => {
    expect(openState([], new Date())).toBeNull()
    const week = weekSchedule(hours)
    expect(week).toHaveLength(7)
    expect(week[0]).toEqual({
      dayOfWeek: 1,
      name: 'Thứ Hai',
      slots: '06:00 – 10:30, 17:00 – 21:00',
    })
    expect(week[1]!.slots).toBe('')
  })
})

describe('Định dạng thẻ / giá', () => {
  it('thẻ kiểu #bình-dân', () => {
    expect(tagLabel(' Bình dân ')).toBe('#bình-dân')
    expect(tagLabel('Hợp  gia đình')).toBe('#hợp-gia-đình')
  })

  it('khoảng giá VND', () => {
    expect(formatPriceRange(null, null)).toBeNull()
    expect(formatPriceRange(50_000, 80_000)).toBe('50.000 – 80.000 ₫')
    expect(formatPriceRange(50_000, 50_000)).toBe('50.000 ₫')
    expect(formatPriceRange(50_000, null)).toBe('từ 50.000 ₫')
    expect(formatPriceRange(null, 80_000)).toBe('đến 80.000 ₫')
  })
})
