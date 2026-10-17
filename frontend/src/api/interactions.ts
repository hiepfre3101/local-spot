import { http } from './http'
import type { Comment, CursorPage, ReportReason, ReportTargetType, VoteResult } from '@/types/api'

/** openapi tag Reviews (vote), Comments, Reports — tương tác trên trang chi tiết (UC14, UC15, UC17). */
export const interactionsApi = {
  /** Idempotent: vote lần hai không đổi gì. Vote review của mình → 409 `SELF_ACTION_FORBIDDEN`. */
  vote(reviewId: number): Promise<VoteResult> {
    return http.put<VoteResult>(`/reviews/${reviewId}/vote`).then((r) => r.data)
  },

  unvote(reviewId: number): Promise<VoteResult> {
    return http.delete<VoteResult>(`/reviews/${reviewId}/vote`).then((r) => r.data)
  },

  /** Bình luận gốc cũ trước, mỗi gốc kèm toàn bộ trả lời. */
  comments(reviewId: number, cursor?: string | null, limit = 10): Promise<CursorPage<Comment>> {
    return http
      .get<CursorPage<Comment>>(`/reviews/${reviewId}/comments`, {
        params: { cursor: cursor ?? undefined, limit },
      })
      .then((r) => r.data)
  },

  /** `parentId` phải là bình luận gốc; ≤ 20 bình luận / 10 phút / tài khoản (429 + Retry-After). */
  addComment(reviewId: number, content: string, parentId: number | null): Promise<Comment> {
    return http
      .post<Comment>(`/reviews/${reviewId}/comments`, { content, parentId })
      .then((r) => r.data)
  },

  deleteComment(commentId: number): Promise<void> {
    return http.delete(`/comments/${commentId}`).then(() => undefined)
  },

  /** 1 báo cáo / người / đối tượng (U10) — đã báo cáo → 409 `REPORT_ALREADY_EXISTS`. */
  report(
    targetType: ReportTargetType,
    targetId: number,
    reason: ReportReason,
    detail?: string,
  ): Promise<void> {
    return http
      .post('/reports', { targetType, targetId, reason, detail: detail || undefined })
      .then(() => undefined)
  },
}
