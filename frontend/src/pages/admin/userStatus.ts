import type { AdminUser } from '@/types/api'

export type UserStatus = 'locked' | 'unverified' | 'active'

/** Trạng thái hiển thị ở P10; khóa ưu tiên hơn chưa xác thực. Khóa đã hết hạn coi như không khóa (như backend). */
export function userStatus(user: AdminUser, now: Date = new Date()): UserStatus {
  if (user.lockedUntil && new Date(user.lockedUntil) > now) {
    return 'locked'
  }
  return user.emailVerified ? 'active' : 'unverified'
}

/** Badge P10: ✓ Hoạt động, ● Chưa xác thực (UC02), Đã khoá. */
export const STATUS_BADGE = {
  active: { tone: 'success', text: '✓ Hoạt động' },
  unverified: { tone: 'warning', text: '● Chưa xác thực' },
  locked: { tone: 'neutral', text: 'Đã khoá' },
} as const
