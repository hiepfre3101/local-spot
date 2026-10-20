import { z } from 'zod'

import type { OpeningHour } from '@/types/api'

/**
 * Luật validate đề xuất địa điểm — đồng bộ với `PlaceCreateRequest` (backend) để báo lỗi ngay trên form; backend vẫn
 * kiểm lại (danh mục / tiện ích tồn tại, `priceMin ≤ priceMax`) và lỗi 422 của nó gắn vào đúng trường.
 */
export const TAGS_MAX = 10
export const TAG_MAX_LENGTH = 50
export const PHONE = /^[0-9+()\s.-]{6,20}$/
export const WEBSITE = /^https?:\/\/\S+$/

/** "1 = Thứ Hai … 7 = Chủ nhật" (openapi `OpeningHour`). */
export const WEEKDAYS: readonly { day: number; label: string }[] = [
  { day: 1, label: 'Thứ Hai' },
  { day: 2, label: 'Thứ Ba' },
  { day: 3, label: 'Thứ Tư' },
  { day: 4, label: 'Thứ Năm' },
  { day: 5, label: 'Thứ Sáu' },
  { day: 6, label: 'Thứ Bảy' },
  { day: 7, label: 'Chủ nhật' },
]

/** Một ngày trên form: đóng cửa, hoặc một khung giờ (đóng < mở = qua đêm — openapi). */
export interface DayHours {
  open: boolean
  openTime: string
  closeTime: string
}

export function defaultWeek(): DayHours[] {
  return WEEKDAYS.map(() => ({ open: false, openTime: '07:00', closeTime: '22:00' }))
}

/** Form → `openingHours` của API: chỉ ngày có mở cửa. */
export function toOpeningHours(week: readonly DayHours[]): OpeningHour[] {
  return week.flatMap((d, i) =>
    d.open ? [{ dayOfWeek: i + 1, openTime: d.openTime, closeTime: d.closeTime }] : [],
  )
}

/**
 * `openingHours` của API → form (màn sửa của chủ — P09). Form một khung / ngày: ngày có nhiều khung lấy khung mở sớm
 * nhất (dữ liệu nhập qua LocalSpot luôn một khung / ngày); ngày vắng = đóng cửa. `HH:mm:ss` → `HH:mm` cho ô giờ.
 */
export function fromOpeningHours(hours: readonly OpeningHour[]): DayHours[] {
  return WEEKDAYS.map(({ day }) => {
    const first = hours
      .filter((h) => h.dayOfWeek === day)
      .sort((a, b) => a.openTime.localeCompare(b.openTime))[0]
    return first
      ? { open: true, openTime: first.openTime.slice(0, 5), closeTime: first.closeTime.slice(0, 5) }
      : { open: false, openTime: '07:00', closeTime: '22:00' }
  })
}

/**
 * "view đẹp, hợp gia đình , view đẹp" → `["view đẹp", "hợp gia đình"]`: tách theo dấu phẩy, bỏ khoảng trắng thừa và
 * thẻ trùng (không phân biệt hoa thường) — backend cũng gộp thẻ trùng slug.
 */
export function parseTags(raw: string): string[] {
  const seen = new Set<string>()
  const tags: string[] = []
  for (const part of raw.split(',')) {
    const tag = part.trim().replace(/\s+/g, ' ')
    const key = tag.toLocaleLowerCase('vi')
    if (tag && !seen.has(key)) {
      seen.add(key)
      tags.push(tag)
    }
  }
  return tags
}

const optionalPrice = z
  .union([z.number().int('Nhập số nguyên.').min(0, 'Không âm.'), z.nan(), z.undefined(), z.null()])
  .transform((v) => (typeof v === 'number' && !Number.isNaN(v) ? v : null))

export const placeSchema = z
  .object({
    name: z
      .string({ required_error: 'Nhập tên địa điểm.' })
      .trim()
      .min(2, 'Tên cần ít nhất 2 ký tự.')
      .max(200, 'Tên tối đa 200 ký tự.'),
    categoryId: z.number({
      required_error: 'Chọn danh mục.',
      invalid_type_error: 'Chọn danh mục.',
    }),
    tags: z
      .string()
      .default('')
      .refine((v) => parseTags(v).length <= TAGS_MAX, `Tối đa ${TAGS_MAX} thẻ.`)
      .refine(
        (v) => parseTags(v).every((t) => t.length <= TAG_MAX_LENGTH),
        `Mỗi thẻ tối đa ${TAG_MAX_LENGTH} ký tự.`,
      ),
    description: z.string().trim().max(5000, 'Mô tả tối đa 5.000 ký tự.').default(''),
    address: z
      .string({ required_error: 'Nhập địa chỉ.' })
      .trim()
      .min(1, 'Nhập địa chỉ.')
      .max(300, 'Địa chỉ tối đa 300 ký tự.'),
    city: z
      .string({ required_error: 'Nhập thành phố.' })
      .trim()
      .min(1, 'Nhập thành phố.')
      .max(100, 'Tối đa 100 ký tự.'),
    location: z.object(
      { lat: z.number(), lng: z.number() },
      {
        required_error: 'Chọn vị trí trên bản đồ.',
        invalid_type_error: 'Chọn vị trí trên bản đồ.',
      },
    ),
    priceMin: optionalPrice,
    priceMax: optionalPrice,
    phone: z
      .string()
      .trim()
      .default('')
      .refine((v) => v === '' || PHONE.test(v), 'Số điện thoại không hợp lệ.'),
    website: z
      .string()
      .trim()
      .max(300, 'Tối đa 300 ký tự.')
      .default('')
      .refine(
        (v) => v === '' || WEBSITE.test(v),
        'Địa chỉ web phải bắt đầu bằng http:// hoặc https://.',
      ),
  })
  .refine((v) => v.priceMin === null || v.priceMax === null || v.priceMin <= v.priceMax, {
    message: 'Giá thấp nhất phải nhỏ hơn hoặc bằng giá cao nhất.',
    path: ['priceMax'],
  })

export type PlaceForm = z.input<typeof placeSchema>
export type PlaceFormValues = z.output<typeof placeSchema>
