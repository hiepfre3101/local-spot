import type { RouteLocationRaw } from 'vue-router'

import { REPORT_REASONS } from '@/components/place/reportTarget'
import type { AppNotification, ReportTargetType } from '@/types/api'

/** Biểu tượng loại (nét 1.8px như bản mẫu P07). */
export const NOTIFICATION_ICONS = {
  check: 'M5 12l5 5 9-10',
  cross: 'M6 6l12 12M18 6L6 18',
  reply: 'M4 5h16v11H9l-5 4z',
  review: 'M12 3l2.6 5.6 6 .6-4.5 4 1.3 6L12 16l-5.4 3.2 1.3-6-4.5-4 6-.6z',
  flag: 'M5 21V4h11l-2 4 2 4H5',
  key: 'M14 10a4 4 0 10-3.5 4L9 16H7v2H5v2H3v-3l7-7',
} as const

export type NotificationIcon = keyof typeof NOTIFICATION_ICONS

/** Một đoạn câu; `strong` = tên in đậm như bản mẫu ("**Tuấn Lê** bình luận…"). Ghép bằng text node, không v-html. */
export interface TextPart {
  text: string
  strong?: boolean
}

export interface NotificationView {
  icon: NotificationIcon
  parts: TextPart[]
  /** Dòng phụ: lý do từ chối / xử lý. */
  detail: string | null
  /** Bấm vào đi đâu; `null` = không có trang tương ứng (nội dung đã bị ẩn, màn kiểm duyệt chưa có). */
  to: RouteLocationRaw | null
}

const TARGET_LABELS: Record<ReportTargetType, string> = {
  PLACE: 'địa điểm',
  REVIEW: 'đánh giá',
  COMMENT: 'bình luận',
  USER: 'tài khoản',
}

function reasonLabel(value: string | undefined): string | null {
  return REPORT_REASONS.find((r) => r.value === value)?.label ?? null
}

function withReason(reason: string | null | undefined): string | null {
  return reason ? `Lý do: ${reason}` : null
}

/**
 * Câu, biểu tượng và đích của từng loại thông báo (bảng loại → `data` trong openapi `Notification`). Mọi chữ dựng từ
 * `data` — ảnh chụp lúc gửi, nên tên địa điểm là tên tại thời điểm đó.
 */
export function describeNotification(n: AppNotification, meId: number): NotificationView {
  const d = n.data
  const place: TextPart = { text: d.placeName ?? 'một địa điểm', strong: true }
  const placePage: RouteLocationRaw | null = d.placeSlug
    ? { name: 'place', params: { slug: d.placeSlug } }
    : null
  const ownerPage: RouteLocationRaw | null =
    d.placeId !== undefined ? { name: 'owner-place', params: { id: d.placeId } } : null

  switch (n.type) {
    case 'PLACE_APPROVED':
      return {
        icon: 'check',
        parts: [{ text: 'Đề xuất ' }, place, { text: ' đã được duyệt và hiển thị' }],
        detail: null,
        to: placePage,
      }
    case 'PLACE_REJECTED':
      return {
        icon: 'cross',
        parts: [{ text: 'Đề xuất ' }, place, { text: ' chưa được duyệt' }],
        detail: withReason(d.reason),
        to: { name: 'profile', params: { id: meId }, query: { tab: 'places' } },
      }
    case 'REVIEW_PUBLISHED':
      return {
        icon: 'check',
        parts: [{ text: 'Đánh giá của bạn ở ' }, place, { text: ' đã được duyệt và hiển thị' }],
        detail: null,
        to: placePage,
      }
    case 'REVIEW_REJECTED':
      return {
        icon: 'cross',
        parts: [{ text: 'Đánh giá của bạn ở ' }, place, { text: ' không được duyệt' }],
        detail: withReason(d.reason),
        to: placePage,
      }
    case 'NEW_REVIEW_ON_MY_PLACE':
      return {
        icon: 'review',
        parts: [
          { text: d.authorName ?? 'Một thành viên', strong: true },
          { text: ' vừa đánh giá ' },
          place,
          ...(d.rating !== undefined ? [{ text: ` · ${d.rating}/5` }] : []),
        ],
        detail: null,
        to: ownerPage ? { ...ownerPage, query: { tab: 'reviews' } } : placePage,
      }
    case 'OWNER_REPLIED':
      return {
        icon: 'reply',
        parts: [
          { text: 'Chủ địa điểm', strong: true },
          { text: ' ' },
          place,
          { text: ' đã phản hồi đánh giá của bạn' },
        ],
        detail: null,
        to: placePage,
      }
    case 'REPORT_ACTION_TAKEN': {
      const target = TARGET_LABELS[d.targetType ?? 'REVIEW']
      const reason = reasonLabel(d.confirmedReason)
      return {
        icon: 'flag',
        parts: [
          { text: `${capitalize(target)} của bạn` },
          ...(d.preview ? [{ text: ' ' }, { text: `“${d.preview}”`, strong: true }] : []),
          { text: ' đã bị ẩn sau khi xét báo cáo' },
        ],
        detail:
          [reason && `Lý do: ${reason}`, d.violation ? 'Ghi nhận một vi phạm' : null]
            .filter(Boolean)
            .join(' · ') || null,
        to: null,
      }
    }
    case 'REPORT_RESOLVED': {
      const target = TARGET_LABELS[d.targetType ?? 'REVIEW']
      return {
        icon: 'flag',
        parts: [
          { text: `Báo cáo của bạn về một ${target}` },
          ...(d.preview ? [{ text: ' ' }, { text: `“${d.preview}”`, strong: true }] : []),
          { text: ' đã được xử lý' },
        ],
        detail: null,
        to: null,
      }
    }
    case 'CLAIM_SUBMITTED':
      return {
        icon: 'key',
        parts: [{ text: 'Có yêu cầu xác nhận sở hữu mới cho ' }, place],
        detail: null,
        // Màn kiểm duyệt yêu cầu sở hữu chưa có ở frontend — gắn liên kết khi làm trang quản trị
        to: null,
      }
    case 'CLAIM_APPROVED':
      return {
        icon: 'key',
        parts: [
          { text: 'Yêu cầu sở hữu ' },
          place,
          { text: ' đã được duyệt — bạn có thể quản lý địa điểm này' },
        ],
        detail: null,
        to: ownerPage,
      }
    case 'CLAIM_REJECTED':
      return {
        icon: 'cross',
        parts: [{ text: 'Yêu cầu sở hữu ' }, place, { text: ' bị từ chối' }],
        detail: withReason(d.reason),
        to: { name: 'owner' },
      }
  }
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}
