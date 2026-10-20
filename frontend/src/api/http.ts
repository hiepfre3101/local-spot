import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

/**
 * Axios dùng chung cho mọi lời gọi `/api/v1`.
 *
 * - Access token chỉ nằm trong bộ nhớ (store auth), gắn vào header `Authorization` mỗi request; refresh token nằm trong
 *   cookie HttpOnly mà JavaScript không đọc được (S1) — `withCredentials` để trình duyệt gửi cookie.
 * - Gặp 401 ở API thường: làm mới phiên **một lần** rồi gửi lại request; làm mới thất bại → báo hết phiên. Các endpoint
 *   xác thực công khai ({@link SESSIONLESS}) không đi qua luồng này: 401 của đăng nhập là sai mật khẩu, không phải
 *   hết phiên; chúng cũng không mang access token.
 *
 * Module này không import store (tránh vòng phụ thuộc): store đăng ký cầu nối qua {@link connectAuth} lúc khởi động.
 */
export const http = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  headers: { Accept: 'application/json' },
})

export interface AuthBridge {
  getAccessToken(): string | null
  /** Làm mới phiên; trả access token mới, hoặc `null` nếu không còn phiên. Phải gộp các lời gọi đồng thời. */
  refresh(): Promise<string | null>
  /** Phiên đã hết (làm mới thất bại / tài khoản bị khóa). */
  onSessionExpired(reason: SessionEndReason): void
}

export type SessionEndReason = 'expired' | 'locked'

let bridge: AuthBridge | null = null

export function connectAuth(authBridge: AuthBridge | null): void {
  bridge = authBridge
}

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

/** Endpoint xác thực bằng body / cookie — không gắn access token, không tự làm mới phiên khi 401. */
const SESSIONLESS = new Set([
  '/auth/register',
  '/auth/login',
  '/auth/refresh',
  '/auth/logout',
  '/auth/verify-email',
  '/auth/forgot-password',
  '/auth/reset-password',
])

function isSessionless(url: string | undefined): boolean {
  return url !== undefined && SESSIONLESS.has(url.replace(/^\/api\/v1/, '').split('?')[0] ?? '')
}

http.interceptors.request.use((config) => {
  const token = bridge?.getAccessToken()
  if (token && !isSessionless(config.url)) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<{ code?: string }>) => {
    const config = error.config as RetriableConfig | undefined
    if (!bridge || !config || error.response?.status !== 401 || isSessionless(config.url)) {
      throw error
    }
    // Tài khoản bị khóa: làm mới cũng vô ích (refresh token đã bị thu hồi khi khóa)
    if (error.response.data?.code === 'ACCOUNT_LOCKED') {
      bridge.onSessionExpired('locked')
      throw error
    }
    if (config._retried) {
      bridge.onSessionExpired('expired')
      throw error
    }
    config._retried = true
    const token = await bridge.refresh()
    if (!token) {
      bridge.onSessionExpired('expired')
      throw error
    }
    config.headers.Authorization = `Bearer ${token}`
    return http(config)
  },
)
