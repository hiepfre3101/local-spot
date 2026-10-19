import { http } from './http'
import type {
  CursorPage,
  EvidenceLink,
  ModerationClaim,
  ModerationDecision,
  ModerationItemType,
  ModerationPlaceDetail,
  ModerationQueueItem,
  ModerationReviewDetail,
  ReportDecision,
  ReportGroupDetail,
  ReportTargetType,
} from '@/types/api'

/** openapi tag Moderation (UC27–UC30). Quyền kiểm ở backend theo từng loại; frontend chỉ ẩn mục không có quyền. */
export const moderationApi = {
  /** Hàng chờ gộp FIFO — cũ nhất trước, thứ tự cố định. `type` bỏ trống = mọi loại người gọi có quyền. */
  queue(
    type: ModerationItemType | null,
    cursor?: string | null,
    limit = 20,
  ): Promise<CursorPage<ModerationQueueItem>> {
    return http
      .get<CursorPage<ModerationQueueItem>>('/moderation/queue', {
        params: { type: type ?? undefined, cursor: cursor ?? undefined, limit },
      })
      .then((r) => r.data)
  },

  place(placeId: number): Promise<ModerationPlaceDetail> {
    return http.get<ModerationPlaceDetail>(`/moderation/places/${placeId}`).then((r) => r.data)
  },

  decidePlace(placeId: number, decision: ModerationDecision): Promise<void> {
    return http.post(`/moderation/places/${placeId}/decision`, decision).then(() => undefined)
  },

  review(reviewId: number): Promise<ModerationReviewDetail> {
    return http.get<ModerationReviewDetail>(`/moderation/reviews/${reviewId}`).then((r) => r.data)
  },

  decideReview(reviewId: number, decision: ModerationDecision): Promise<void> {
    return http.post(`/moderation/reviews/${reviewId}/decision`, decision).then(() => undefined)
  },

  reportGroup(targetType: ReportTargetType, targetId: number): Promise<ReportGroupDetail> {
    return http
      .get<ReportGroupDetail>(`/moderation/reports/${targetType}/${targetId}`)
      .then((r) => r.data)
  },

  decideReports(decision: ReportDecision): Promise<void> {
    return http.post('/moderation/reports/decision', decision).then(() => undefined)
  },

  claim(claimId: number): Promise<ModerationClaim> {
    return http.get<ModerationClaim>(`/moderation/claims/${claimId}`).then((r) => r.data)
  },

  /** Link tạm 5 phút — ảnh minh chứng nằm trong bucket riêng tư; hết hạn thì gọi lại để ký link mới. */
  evidence(claimId: number, index: number): Promise<EvidenceLink> {
    return http
      .get<EvidenceLink>(`/moderation/claims/${claimId}/evidence/${index}`)
      .then((r) => r.data)
  },

  decideClaim(claimId: number, decision: ModerationDecision): Promise<void> {
    return http.post(`/moderation/claims/${claimId}/decision`, decision).then(() => undefined)
  },
}
