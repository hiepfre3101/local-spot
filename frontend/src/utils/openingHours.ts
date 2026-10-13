import type { OpeningHour } from '@/types/api'

/** 1 = Thứ Hai … 7 = Chủ nhật (openapi `OpeningHour.dayOfWeek`, ISO-8601). */
export const DAY_NAMES = [
  'Thứ Hai',
  'Thứ Ba',
  'Thứ Tư',
  'Thứ Năm',
  'Thứ Sáu',
  'Thứ Bảy',
  'Chủ nhật',
] as const

export interface DaySchedule {
  dayOfWeek: number
  name: string
  /** "06:00 – 10:30, 17:00 – 21:00"; rỗng = nghỉ. */
  slots: string
}

export interface OpenState {
  open: boolean
  /** Khung giờ hôm nay để hiện cạnh trạng thái ("17:00 – 23:00"); rỗng nếu hôm nay nghỉ. */
  today: string
}

/** Thời điểm hiện tại theo giờ Việt Nam — giờ mở cửa là giờ địa phương của quán, không theo múi giờ của máy xem. */
export function vietnamClock(now: Date): { dayOfWeek: number; minutes: number } {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(now)
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
  const day = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].indexOf(get('weekday')) + 1
  return { dayOfWeek: day, minutes: Number(get('hour')) * 60 + Number(get('minute')) }
}

/** Lịch 7 ngày, khung giờ trong ngày sắp theo giờ mở. */
export function weekSchedule(hours: readonly OpeningHour[]): DaySchedule[] {
  return DAY_NAMES.map((name, i) => ({
    dayOfWeek: i + 1,
    name,
    slots: slotsOf(hours, i + 1),
  }))
}

/**
 * Đang mở hay không. Khung qua đêm (`closeTime < openTime`, vd. 18:00 – 02:00 thứ Bảy) còn hiệu lực tới giờ đóng của
 * **sáng hôm sau** — 01:00 Chủ nhật vẫn là "Đang mở". `null` khi địa điểm chưa khai giờ mở cửa.
 */
export function openState(hours: readonly OpeningHour[], now: Date): OpenState | null {
  if (hours.length === 0) return null
  const { dayOfWeek, minutes } = vietnamClock(now)
  const yesterday = dayOfWeek === 1 ? 7 : dayOfWeek - 1
  const open =
    hours.some((h) => {
      if (h.dayOfWeek !== dayOfWeek) return false
      const start = toMinutes(h.openTime)
      const end = toMinutes(h.closeTime)
      return end > start ? minutes >= start && minutes < end : minutes >= start
    }) ||
    hours.some((h) => {
      if (h.dayOfWeek !== yesterday) return false
      const end = toMinutes(h.closeTime)
      return end < toMinutes(h.openTime) && minutes < end
    })
  return { open, today: slotsOf(hours, dayOfWeek) }
}

function slotsOf(hours: readonly OpeningHour[], dayOfWeek: number): string {
  return hours
    .filter((h) => h.dayOfWeek === dayOfWeek)
    .sort((a, b) => toMinutes(a.openTime) - toMinutes(b.openTime))
    .map((h) => `${h.openTime} – ${h.closeTime}`)
    .join(', ')
}

function toMinutes(hhmm: string): number {
  const [h, m] = hhmm.split(':').map(Number)
  return (h ?? 0) * 60 + (m ?? 0)
}
