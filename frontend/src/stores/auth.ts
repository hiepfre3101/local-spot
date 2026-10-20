import { defineStore } from 'pinia'
import { computed, ref, shallowRef } from 'vue'

import { authApi } from '@/api/auth'
import { meApi } from '@/api/me'
import type { AuthResponse, MeResponse } from '@/types/api'

/** Kênh báo đăng xuất giữa các tab — đăng xuất ở một tab thì các tab khác cũng bỏ phiên. */
const CHANNEL_NAME = 'localspot-auth'
/** Khóa Web Locks: chỉ một tab làm mới phiên tại một thời điểm. */
const REFRESH_LOCK = 'localspot-refresh'

/**
 * Phiên đăng nhập (UC03, UC04).
 *
 * - Access token chỉ giữ trong bộ nhớ: tải lại trang thì khôi phục phiên bằng `POST /auth/refresh` (cookie HttpOnly),
 *   không lưu token vào localStorage — script bị chèn (XSS) không đọc được token dài hạn.
 * - **Làm mới một lần cho nhiều request**: các request 401 cùng lúc chờ chung một lần làm mới. Backend xoay vòng
 *   refresh token và coi việc dùng lại token cũ là bị đánh cắp (thu hồi mọi phiên — U2), nên hai lần làm mới song song
 *   bằng cùng một cookie sẽ tự đăng xuất người dùng. Vì vậy còn khóa **giữa các tab** bằng Web Locks: tab sau chờ tab
 *   trước xong, lúc đó trình duyệt đã gửi cookie mới.
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<MeResponse | null>(null)
  const accessToken = shallowRef<string | null>(null)

  let initPromise: Promise<void> | null = null
  let refreshPromise: Promise<string | null> | null = null
  const channel =
    typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(CHANNEL_NAME)
  channel?.addEventListener('message', (event: MessageEvent<string>) => {
    if (event.data === 'logout') {
      clearSession()
    }
  })

  const isAuthenticated = computed(() => user.value !== null && accessToken.value !== null)
  const permissions = computed(() => new Set(user.value?.permissions ?? []))

  function can(permission: string): boolean {
    return permissions.value.has(permission)
  }

  function canAny(required: readonly string[]): boolean {
    return required.some((p) => permissions.value.has(p))
  }

  function setSession(response: AuthResponse): void {
    accessToken.value = response.accessToken
    user.value = response.user
  }

  function clearSession(): void {
    accessToken.value = null
    user.value = null
  }

  /** Khôi phục phiên khi mở app — gọi một lần, các lần sau dùng lại kết quả. */
  function init(): Promise<void> {
    initPromise ??= refresh().then(() => undefined)
    return initPromise
  }

  function refresh(): Promise<string | null> {
    refreshPromise ??= withRefreshLock(async () => {
      try {
        const response = await authApi.refresh()
        setSession(response)
        return response.accessToken
      } catch {
        // Không có cookie / hết hạn / đã bị thu hồi → khách
        clearSession()
        return null
      }
    }).finally(() => {
      refreshPromise = null
    })
    return refreshPromise
  }

  async function login(email: string, password: string): Promise<void> {
    setSession(await authApi.login({ email, password }))
  }

  /** Đăng xuất luôn thành công với người dùng: lỗi mạng vẫn xóa phiên ở trình duyệt. */
  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } catch {
      // backend vẫn xóa cookie khi gọi lại được; phía trình duyệt bỏ phiên ngay
    } finally {
      clearSession()
      channel?.postMessage('logout')
    }
  }

  /** Nạp lại hồ sơ sau khi thay đổi phía máy chủ (xác thực email, đổi role...). */
  async function reloadUser(): Promise<void> {
    if (accessToken.value) {
      user.value = await meApi.get()
    }
  }

  return {
    user,
    accessToken,
    isAuthenticated,
    permissions,
    can,
    canAny,
    init,
    refresh,
    login,
    logout,
    reloadUser,
    clearSession,
  }
})

/** Chạy `task` trong khóa giữa các tab nếu trình duyệt hỗ trợ Web Locks; không hỗ trợ thì chạy thẳng. */
function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.locks) {
    return navigator.locks.request(REFRESH_LOCK, task)
  }
  return task()
}
