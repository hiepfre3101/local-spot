import type { RoleName } from '@/types/api'

/**
 * Luật gán vai trò ở P10 — phản chiếu luật backend đã chốt 2026-10-03 (`AdminUserService`) để giao diện không cho tạo
 * yêu cầu chắc chắn bị từ chối:
 * - USER: mọi tài khoản luôn có, không bỏ được.
 * - OWNER: chỉ cấp qua duyệt yêu cầu sở hữu (UC30) — hiển thị hiện trạng, không sửa ở đây.
 * - MODERATOR, ADMIN: gán / gỡ được (cần `user:assign-role`); admin không tự gỡ ADMIN của mình.
 *
 * Khác bản thiết kế P10 (radio một vai trò): dùng ô chọn vì một tài khoản có thể giữ nhiều vai trò cùng lúc (vd. chủ
 * địa điểm kiêm kiểm duyệt viên) và API nhận cả danh sách.
 */
export const EDITABLE_ROLES = ['MODERATOR', 'ADMIN'] as const
export type EditableRole = (typeof EDITABLE_ROLES)[number]

export interface RoleOption {
  role: RoleName
  checked: boolean
  disabled: boolean
  /** Lý do không sửa được — hiện dưới ô chọn. */
  note: string | null
}

export interface RoleContext {
  current: readonly RoleName[]
  draft: ReadonlySet<EditableRole>
  isSelf: boolean
  canAssign: boolean
}

export function roleOptions({ current, draft, isSelf, canAssign }: RoleContext): RoleOption[] {
  const selfAdmin = isSelf && current.includes('ADMIN')
  return [
    { role: 'USER', checked: true, disabled: true, note: 'Mọi tài khoản đều có.' },
    {
      role: 'OWNER',
      checked: current.includes('OWNER'),
      disabled: true,
      note: 'Chỉ cấp qua duyệt yêu cầu sở hữu.',
    },
    { role: 'MODERATOR', checked: draft.has('MODERATOR'), disabled: !canAssign, note: null },
    {
      role: 'ADMIN',
      checked: draft.has('ADMIN'),
      disabled: !canAssign || selfAdmin,
      note: selfAdmin ? 'Không thể tự gỡ vai trò quản trị của mình.' : null,
    },
  ]
}

export function editableRolesOf(roles: readonly RoleName[]): Set<EditableRole> {
  return new Set(EDITABLE_ROLES.filter((r) => roles.includes(r)))
}

/** Danh sách gửi `PUT /admin/users/{id}/roles`: luôn có USER, OWNER giữ nguyên hiện trạng, cộng phần chỉnh được. */
export function buildRoleList(
  current: readonly RoleName[],
  draft: ReadonlySet<EditableRole>,
): RoleName[] {
  const roles: RoleName[] = ['USER']
  if (current.includes('OWNER')) {
    roles.push('OWNER')
  }
  EDITABLE_ROLES.forEach((r) => draft.has(r) && roles.push(r))
  return roles
}

export function hasRoleChanges(
  current: readonly RoleName[],
  draft: ReadonlySet<EditableRole>,
): boolean {
  return EDITABLE_ROLES.some((r) => current.includes(r) !== draft.has(r))
}
