import { z } from 'zod'

/** Đồng bộ với backend (UC12, U4): nội dung 20–5000 ký tự sau khi bỏ khoảng trắng hai đầu, ngày đến ≤ hôm nay giờ VN. */
export const REVIEW_CONTENT_MIN = 20
export const REVIEW_CONTENT_MAX = 5000
export const REVIEW_PHOTOS_MAX = 10

/** Mức điểm kèm chữ mô tả (P04 — Rating). */
export const RATING_LABELS: Record<number, string> = {
  1: 'Tệ',
  2: 'Chưa tốt',
  3: 'Ổn',
  4: 'Rất tốt',
  5: 'Tuyệt vời',
}

/** Hôm nay theo giờ Việt Nam, dạng `YYYY-MM-DD` — backend so `visitedAt` với ngày này, không theo múi giờ máy. */
export function todayInVietnam(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(now)
}

export function reviewSchema(today: string = todayInVietnam()) {
  return z.object({
    rating: z
      .number({ required_error: 'Chọn số điểm.', invalid_type_error: 'Chọn số điểm.' })
      .int()
      .min(1, 'Chọn số điểm.')
      .max(5, 'Chọn số điểm.'),
    content: z
      .string({ required_error: 'Kể lại trải nghiệm của bạn.' })
      .trim()
      .min(REVIEW_CONTENT_MIN, `Viết ít nhất ${REVIEW_CONTENT_MIN} ký tự.`)
      .max(REVIEW_CONTENT_MAX, `Tối đa ${REVIEW_CONTENT_MAX} ký tự.`),
    visitedAt: z
      .string({ required_error: 'Chọn ngày bạn đến.' })
      .regex(/^\d{4}-\d{2}-\d{2}$/, 'Chọn ngày bạn đến.')
      .refine((day) => day <= today, 'Ngày đến không được ở tương lai.'),
  })
}

export type ReviewForm = z.infer<ReturnType<typeof reviewSchema>>
