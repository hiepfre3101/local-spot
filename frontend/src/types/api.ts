/**
 * Kiểu dữ liệu API — bám docs/api/openapi.yaml (schema cùng tên). Chỉ khai báo phần frontend đang dùng; thêm dần theo
 * từng module.
 */

export type RoleName = 'USER' | 'OWNER' | 'MODERATOR' | 'ADMIN'

/** openapi `MeResponse`. `permissions` gộp từ các role — dùng để ẩn / chặn theo đúng quyền của backend. */
export interface MeResponse {
  id: number
  displayName: string
  avatarUrl: string | null
  email: string
  bio: string | null
  emailVerified: boolean
  roles: RoleName[]
  permissions: string[]
  trustScore: number
  ownedPlaceIds: number[]
}

/** openapi `AuthResponse`. Refresh token không có ở đây — nằm trong cookie HttpOnly (S1). */
export interface AuthResponse {
  accessToken: string
  expiresIn: number
  user: MeResponse
}

/** Danh sách phân trang cursor: `nextCursor = null` là hết. */
export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
}

/** openapi `AdminUser` (UC31). */
export interface AdminUser {
  id: number
  displayName: string
  avatarUrl: string | null
  email: string
  emailVerified: boolean
  roles: RoleName[]
  trustScore: number
  lockedUntil: string | null
  createdAt: string
}

/** openapi `Problem` — RFC 7807. */
export interface ProblemBody {
  type?: string
  title?: string
  status?: number
  detail?: string
  code?: string
  errors?: { field: string; message: string }[]
  errorId?: string
}
