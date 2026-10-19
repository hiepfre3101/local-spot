import dayjs from 'dayjs'
import 'dayjs/locale/vi'

dayjs.locale('vi')

/** Ngày kiểu Việt Nam: 14/03/2025. */
export function formatDate(iso: string): string {
  return dayjs(iso).format('DD/MM/YYYY')
}

/** Ngày giờ: 08:10 27/09/2026. */
export function formatDateTime(iso: string): string {
  return dayjs(iso).format('HH:mm DD/MM/YYYY')
}

/** Số theo định dạng Việt Nam: 12.480 (README design system — Giọng văn). */
export function formatNumber(value: number): string {
  return new Intl.NumberFormat('vi-VN').format(value)
}

/** Chữ cái đầu cho ảnh đại diện giữ chỗ: "Minh Anh" → "MA". */
export function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean)
  const letters = words.length > 1 ? [words[0], words[words.length - 1]] : words
  return letters
    .map((w) => w?.charAt(0) ?? '')
    .join('')
    .toUpperCase()
}

/** "Thử lại sau N phút / giây" từ header Retry-After (giây). */
export function retryAfterText(seconds: number): string {
  return seconds >= 60 ? `${Math.ceil(seconds / 60)} phút` : `${seconds} giây`
}

/** Thẻ hiển thị kiểu `#bình-dân` như mẫu SpotCard (giữ dấu tiếng Việt, khoảng trắng thành gạch nối). */
export function tagLabel(name: string): string {
  return `#${name.trim().toLocaleLowerCase('vi').replace(/\s+/g, '-')}`
}

/** Khoảng giá VND: "50.000 – 80.000 ₫", "từ 50.000 ₫"; `null` khi chưa khai giá. */
export function formatPriceRange(min: number | null, max: number | null): string | null {
  if (min === null && max === null) return null
  if (min !== null && max !== null) {
    return min === max ? `${formatNumber(min)} ₫` : `${formatNumber(min)} – ${formatNumber(max)} ₫`
  }
  return min !== null ? `từ ${formatNumber(min)} ₫` : `đến ${formatNumber(max!)} ₫`
}

/**
 * Thời gian tương đối như bản mẫu P07: "Vừa xong", "5 phút trước", "3 giờ trước", "Hôm qua", "2 ngày trước"; quá 7 ngày
 * thì ghi ngày (14/03/2025) — "23 ngày trước" khó hình dung hơn một ngày cụ thể.
 */
export function relativeTime(iso: string, now: number = Date.now()): string {
  const then = dayjs(iso)
  const minutes = Math.floor((now - then.valueOf()) / 60_000)
  if (minutes < 1) return 'Vừa xong'
  if (minutes < 60) return `${minutes} phút trước`
  const today = dayjs(now).startOf('day')
  if (minutes < 24 * 60 && !then.isBefore(today)) return `${Math.floor(minutes / 60)} giờ trước`
  const days = today.diff(then.startOf('day'), 'day')
  if (days <= 1) return 'Hôm qua'
  if (days < 7) return `${days} ngày trước`
  return formatDate(iso)
}

/** Ngày giờ gọn cho bảng quản trị: 27/09 08:10 (bản mẫu DataTable). */
export function formatShortDateTime(iso: string): string {
  return dayjs(iso).format('DD/MM HH:mm')
}
