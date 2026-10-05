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
