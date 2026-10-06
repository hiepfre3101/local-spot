import { http } from './http'
import type { AuthResponse } from '@/types/api'

/** openapi tag Auth (UC01–UC05). Refresh token đi qua cookie HttpOnly — không xuất hiện ở đây. */
export const authApi = {
  register(body: { email: string; password: string; displayName: string }): Promise<void> {
    return http.post('/auth/register', body).then(() => undefined)
  },

  login(body: { email: string; password: string }): Promise<AuthResponse> {
    return http.post<AuthResponse>('/auth/login', body).then((r) => r.data)
  },

  refresh(): Promise<AuthResponse> {
    return http.post<AuthResponse>('/auth/refresh').then((r) => r.data)
  },

  logout(): Promise<void> {
    return http.post('/auth/logout').then(() => undefined)
  },

  verifyEmail(token: string): Promise<void> {
    return http.post('/auth/verify-email', { token }).then(() => undefined)
  },

  /** Cần đăng nhập — gửi lại mail cho chính tài khoản đang đăng nhập (3 lần / giờ). */
  resendVerification(): Promise<void> {
    return http.post('/auth/resend-verification').then(() => undefined)
  },

  forgotPassword(email: string): Promise<void> {
    return http.post('/auth/forgot-password', { email }).then(() => undefined)
  },

  resetPassword(token: string, newPassword: string): Promise<void> {
    return http.post('/auth/reset-password', { token, newPassword }).then(() => undefined)
  },
}
