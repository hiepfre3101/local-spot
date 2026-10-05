import { http } from './http'
import type { MeResponse } from '@/types/api'

/** openapi tag Me (UC06). */
export const meApi = {
  get(): Promise<MeResponse> {
    return http.get<MeResponse>('/me').then((r) => r.data)
  },

  /** Thành công: mọi phiên khác bị đăng xuất, thiết bị hiện tại nhận cookie refresh token mới. */
  changePassword(currentPassword: string, newPassword: string): Promise<void> {
    return http.put('/me/password', { currentPassword, newPassword }).then(() => undefined)
  },
}
