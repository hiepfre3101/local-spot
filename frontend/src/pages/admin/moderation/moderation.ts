import type { RouteLocationRaw } from 'vue-router'

import type { Problem } from '@/api/problem'
import { Permission, type PermissionName } from '@/auth/permissions'
import type { ModerationItemType, ModerationQueueItem, ReportTargetType } from '@/types/api'

/** Bốn loại việc theo thứ tự của hàng chờ gộp (openapi: PLACE, REVIEW, REPORT, CLAIM). */
export const MODERATION_TYPES: readonly ModerationItemType[] = [
  'PLACE',
  'REVIEW',
  'REPORT',
  'CLAIM',
]

/** Nhãn cột "Loại" như bản mẫu DataTable. */
export const TYPE_LABELS: Record<ModerationItemType, string> = {
  PLACE: 'Địa điểm',
  REVIEW: 'Đánh giá',
  REPORT: 'Báo cáo',
  CLAIM: 'Sở hữu',
}

/** Quyền xử lý từng loại — khớp `x-permission` của openapi. */
export const TYPE_PERMISSIONS: Record<ModerationItemType, PermissionName> = {
  PLACE: Permission.PLACE_APPROVE,
  REVIEW: Permission.REVIEW_MODERATE,
  REPORT: Permission.REPORT_HANDLE,
  CLAIM: Permission.CLAIM_APPROVE,
}

export const MODERATION_PERMISSIONS: readonly PermissionName[] = Object.values(TYPE_PERMISSIONS)

export const REPORT_TARGET_LABELS: Record<ReportTargetType, string> = {
  PLACE: 'Địa điểm',
  REVIEW: 'Đánh giá',
  COMMENT: 'Bình luận',
  USER: 'Tài khoản',
}

/** Màn xử lý của một dòng hàng chờ (sitemap §2.3). */
export function detailRoute(
  item: Pick<ModerationQueueItem, 'type' | 'id' | 'reportTargetType'>,
): RouteLocationRaw {
  switch (item.type) {
    case 'PLACE':
      return { name: 'admin-moderation-place', params: { id: item.id } }
    case 'REVIEW':
      return { name: 'admin-moderation-review', params: { id: item.id } }
    case 'REPORT':
      return {
        name: 'admin-moderation-report',
        params: { targetType: item.reportTargetType ?? 'REVIEW', targetId: item.id },
      }
    case 'CLAIM':
      return { name: 'admin-moderation-claim', params: { id: item.id } }
  }
}

/** Cột "Đã chờ": "2 ngày 1 giờ", "18 giờ", "14 phút" — tuổi của mục, để thấy việc tồn lâu. */
export function waitedText(iso: string, now: number = Date.now()): string {
  const minutes = Math.max(0, Math.floor((now - new Date(iso).getTime()) / 60_000))
  if (minutes < 60) return `${minutes} phút`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} giờ`
  const days = Math.floor(hours / 24)
  const rest = hours % 24
  return rest > 0 ? `${days} ngày ${rest} giờ` : `${days} ngày`
}

/** Mã 409 nghĩa là "mục đã đổi dưới tay bạn" — màn phải tải lại để thấy kết quả của người khác. */
const STALE_CODES = new Set([
  'PLACE_ALREADY_MODERATED',
  'REVIEW_ALREADY_MODERATED',
  'CLAIM_ALREADY_MODERATED',
  'REPORT_ALREADY_HANDLED',
  'CONCURRENT_MODIFICATION',
])

export function isStale(problem: Problem): boolean {
  return problem.code !== null && STALE_CODES.has(problem.code)
}
