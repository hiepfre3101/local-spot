import type { RoleName } from '@/types/api'

/**
 * Tên permission — khớp `Permissions.java` / bảng `permissions` (V2) / `x-permission` của openapi. Frontend chỉ dùng để
 * ẩn nút và chặn route cho đỡ gọi thừa; backend vẫn là nơi kiểm quyền thật (`@PreAuthorize`).
 */
export const Permission = {
  PLACE_APPROVE: 'place:approve',
  REVIEW_MODERATE: 'review:moderate',
  REPORT_HANDLE: 'report:handle',
  CLAIM_APPROVE: 'claim:approve',
  USER_VIEW: 'user:view',
  USER_LOCK: 'user:lock',
  USER_ASSIGN_ROLE: 'user:assign-role',
  CATEGORY_MANAGE: 'category:manage',
  AMENITY_MANAGE: 'amenity:manage',
  DASHBOARD_VIEW: 'dashboard:view',
} as const

export type PermissionName = (typeof Permission)[keyof typeof Permission]

/** Có ít nhất một quyền này → được vào khu quản trị (`/admin`). */
export const STAFF_PERMISSIONS: readonly PermissionName[] = [
  Permission.PLACE_APPROVE,
  Permission.REVIEW_MODERATE,
  Permission.REPORT_HANDLE,
  Permission.CLAIM_APPROVE,
  Permission.USER_VIEW,
  Permission.CATEGORY_MANAGE,
  Permission.AMENITY_MANAGE,
  Permission.DASHBOARD_VIEW,
]

export const ROLE_LABELS: Record<RoleName, string> = {
  USER: 'Thành viên',
  OWNER: 'Chủ địa điểm',
  MODERATOR: 'Kiểm duyệt viên',
  ADMIN: 'Quản trị viên',
}

/** Mô tả quyền theo P10. */
export const ROLE_DESCRIPTIONS: Record<RoleName, string> = {
  USER: 'Viết, check-in, đề xuất',
  OWNER: '+ quản lý địa điểm đã xác nhận',
  MODERATOR: 'Hàng chờ kiểm duyệt',
  ADMIN: 'Toàn quyền',
}

const ROLE_RANK: RoleName[] = ['ADMIN', 'MODERATOR', 'OWNER', 'USER']

/** Vai trò cao nhất để hiển thị một nhãn (cột "Vai trò" P10, chân sidebar quản trị). */
export function primaryRole(roles: readonly RoleName[]): RoleName {
  return ROLE_RANK.find((r) => roles.includes(r)) ?? 'USER'
}
