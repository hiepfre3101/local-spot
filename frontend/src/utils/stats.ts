import type { DailyCount } from '@/types/api'

/** Hôm nay theo giờ Việt Nam, dạng `YYYY-MM-DD` — thống kê backend tính ngày theo giờ VN. */
export function vietnamToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(now)
}

/** `2026-10-18` + (-6) → `2026-10-12` (lịch thuần, không lệch múi giờ). */
export function shiftDate(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split('-').map(Number) as [number, number, number]
  const date = new Date(Date.UTC(y, m - 1, d + days))
  return date.toISOString().slice(0, 10)
}

export function sum(series: readonly DailyCount[]): number {
  return series.reduce((total, day) => total + day.count, 0)
}

/**
 * Chuỗi `2n` ngày → kỳ này (n ngày cuối) và kỳ trước cùng độ dài, để so sánh như dải KPI của AdminDashboard. Một lần
 * gọi API cho cả hai kỳ.
 */
export function splitPeriods(
  series: readonly DailyCount[],
  days: number,
): { current: DailyCount[]; previous: DailyCount[] } {
  return { current: series.slice(-days), previous: series.slice(-2 * days, -days) }
}

/**
 * Thay đổi so với kỳ trước: `"▲ 9,2%"`, `"▼ 3%"`, `"Không đổi"`; kỳ trước bằng 0 thì không có phần trăm. Mũi tên + chữ
 * — không chỉ bằng màu.
 */
export function deltaText(
  current: number,
  previous: number,
): { text: string; trend: 'up' | 'down' | 'flat' } {
  if (current === previous) return { text: 'Không đổi', trend: 'flat' }
  const trend = current > previous ? 'up' : 'down'
  const arrow = trend === 'up' ? '▲' : '▼'
  if (previous === 0) return { text: `${arrow} ${formatSigned(current - previous)}`, trend }
  const percent = (Math.abs(current - previous) / previous) * 100
  const shown = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(percent)
  return { text: `${arrow} ${shown}%`, trend }
}

function formatSigned(value: number): string {
  return new Intl.NumberFormat('vi-VN').format(Math.abs(value))
}
