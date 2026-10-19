import { http } from './http'
import type { AppNotification, CursorPage } from '@/types/api'

/** openapi tag Me — phần thông báo (UC19, FR-27). Đẩy realtime xem {@link ./notificationSocket}. */
export const notificationsApi = {
  /** Mới nhất trước (keyset theo id); `unreadOnly` lọc ở máy chủ để cursor vẫn đúng. */
  list(
    cursor?: string | null,
    limit = 10,
    unreadOnly = false,
  ): Promise<CursorPage<AppNotification>> {
    return http
      .get<CursorPage<AppNotification>>('/me/notifications', {
        params: { cursor: cursor ?? undefined, limit, unreadOnly: unreadOnly || undefined },
      })
      .then((r) => r.data)
  },

  unreadCount(): Promise<number> {
    return http.get<{ count: number }>('/me/notifications/unread-count').then((r) => r.data.count)
  },

  /** Idempotent — đã đọc thì máy chủ giữ thời điểm đọc cũ. */
  markRead(id: number): Promise<void> {
    return http.post(`/me/notifications/${id}/read`).then(() => undefined)
  },

  markAllRead(): Promise<void> {
    return http.post('/me/notifications/read-all').then(() => undefined)
  },
}
