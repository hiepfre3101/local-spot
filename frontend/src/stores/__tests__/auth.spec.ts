import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AuthResponse } from '@/types/api'

import { useAuthStore } from '../auth'

const refresh = vi.fn<() => Promise<AuthResponse>>()
const logout = vi.fn<() => Promise<void>>()

vi.mock('@/api/auth', () => ({
  authApi: { refresh: () => refresh(), logout: () => logout(), login: vi.fn() },
}))

function session(token: string): AuthResponse {
  return {
    accessToken: token,
    expiresIn: 900,
    user: {
      id: 7,
      displayName: 'Minh Anh',
      avatarUrl: null,
      email: 'ma@localspot.test',
      bio: null,
      emailVerified: true,
      roles: ['USER', 'MODERATOR'],
      permissions: ['place:approve', 'review:create'],
      trustScore: 40,
      ownedPlaceIds: [],
    },
  }
}

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    refresh.mockReset()
    logout.mockReset()
  })

  it('nhiều lần làm mới đồng thời chỉ gọi /auth/refresh một lần (refresh token xoay vòng — U2)', async () => {
    refresh.mockImplementation(
      () => new Promise((resolve) => setTimeout(() => resolve(session('t1')), 5)),
    )
    const auth = useAuthStore()

    const tokens = await Promise.all([auth.refresh(), auth.refresh(), auth.refresh()])

    expect(refresh).toHaveBeenCalledTimes(1)
    expect(tokens).toEqual(['t1', 't1', 't1'])
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.can('place:approve')).toBe(true)
    expect(auth.canAny(['user:view', 'review:create'])).toBe(true)
    expect(auth.can('user:view')).toBe(false)
  })

  it('làm mới thất bại (không có cookie) → khách, không ném lỗi', async () => {
    refresh.mockRejectedValue(new Error('401'))
    const auth = useAuthStore()

    await auth.init()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.user).toBeNull()
  })

  it('đăng xuất luôn xóa phiên ở trình duyệt, kể cả khi gọi máy chủ lỗi', async () => {
    refresh.mockResolvedValue(session('t1'))
    logout.mockRejectedValue(new Error('network'))
    const auth = useAuthStore()
    await auth.init()

    await auth.logout()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.accessToken).toBeNull()
  })
})
