import { describe, expect, it } from 'vitest'

import type { AdminUser } from '@/types/api'

import {
  buildRoleList,
  editableRolesOf,
  hasRoleChanges,
  roleOptions,
  type EditableRole,
} from '../roleRules'
import { userStatus } from '../userStatus'

describe('luật vai trò P10 (khớp AdminUserService)', () => {
  it('USER luôn có, OWNER giữ nguyên hiện trạng khi gửi danh sách mới', () => {
    expect(buildRoleList(['USER', 'OWNER'], new Set<EditableRole>(['MODERATOR']))).toEqual([
      'USER',
      'OWNER',
      'MODERATOR',
    ])
    expect(buildRoleList(['USER', 'ADMIN'], new Set())).toEqual(['USER'])
  })

  it('USER và OWNER không sửa được; admin không tự gỡ ADMIN của mình', () => {
    const options = roleOptions({
      current: ['USER', 'ADMIN'],
      draft: new Set<EditableRole>(['ADMIN']),
      isSelf: true,
      canAssign: true,
    })
    const byRole = Object.fromEntries(options.map((o) => [o.role, o]))
    expect(byRole.USER).toMatchObject({ checked: true, disabled: true })
    expect(byRole.OWNER).toMatchObject({ checked: false, disabled: true })
    expect(byRole.MODERATOR).toMatchObject({ disabled: false })
    expect(byRole.ADMIN).toMatchObject({ checked: true, disabled: true })
    expect(byRole.ADMIN?.note).toContain('Không thể tự gỡ')
  })

  it('không có quyền gán vai trò → mọi ô khóa', () => {
    const options = roleOptions({
      current: ['USER'],
      draft: new Set(),
      isSelf: false,
      canAssign: false,
    })
    expect(options.every((o) => o.disabled)).toBe(true)
  })

  it('chỉ báo có thay đổi khi phần chỉnh được khác hiện trạng', () => {
    const current = ['USER', 'OWNER', 'MODERATOR'] as const
    expect(hasRoleChanges(current, editableRolesOf([...current]))).toBe(false)
    expect(hasRoleChanges(current, new Set<EditableRole>(['MODERATOR', 'ADMIN']))).toBe(true)
  })
})

describe('trạng thái người dùng', () => {
  const base: AdminUser = {
    id: 1,
    displayName: 'A',
    avatarUrl: null,
    email: 'a@x.vn',
    emailVerified: true,
    roles: ['USER'],
    trustScore: 0,
    lockedUntil: null,
    createdAt: '2026-10-01T00:00:00Z',
  }
  const now = new Date('2026-10-05T00:00:00Z')

  it('khóa còn hạn ưu tiên hơn chưa xác thực; khóa đã hết hạn coi như không khóa', () => {
    expect(
      userStatus({ ...base, emailVerified: false, lockedUntil: '2026-10-06T00:00:00Z' }, now),
    ).toBe('locked')
    expect(userStatus({ ...base, lockedUntil: '2026-10-04T00:00:00Z' }, now)).toBe('active')
    expect(userStatus({ ...base, emailVerified: false }, now)).toBe('unverified')
  })
})
