import { http } from './http'
import type { AdminUser, CursorPage, RoleName } from '@/types/api'

export interface AdminUserFilter {
  q?: string
  role?: RoleName
  locked?: boolean
}

/** openapi `/admin/users` — quản lý người dùng & vai trò (UC31). */
export const adminUsersApi = {
  search(
    filter: AdminUserFilter,
    cursor?: string | null,
    limit = 20,
  ): Promise<CursorPage<AdminUser>> {
    return http
      .get<CursorPage<AdminUser>>('/admin/users', {
        params: {
          q: filter.q?.trim() || undefined,
          role: filter.role,
          locked: filter.locked,
          cursor: cursor ?? undefined,
          limit,
        },
      })
      .then((r) => r.data)
  },

  /** Khóa tới `until` (tương lai) kèm lý do 1–500 ký tự; khóa lại = cập nhật thời hạn / lý do. */
  lock(userId: number, until: string, reason: string): Promise<void> {
    return http.post(`/admin/users/${userId}/lock`, { until, reason }).then(() => undefined)
  },

  unlock(userId: number): Promise<void> {
    return http.delete(`/admin/users/${userId}/lock`).then(() => undefined)
  },

  /** Thay toàn bộ danh sách role (luôn gồm USER; OWNER giữ nguyên hiện trạng — chỉ cấp qua UC30). */
  replaceRoles(userId: number, roles: RoleName[]): Promise<void> {
    return http.put(`/admin/users/${userId}/roles`, roles).then(() => undefined)
  },
}
