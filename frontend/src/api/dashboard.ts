import { http } from './http'
import type { Dashboard } from '@/types/api'

/** `GET /admin/dashboard` (UC33) — cần `dashboard:view`. Không cache phía máy chủ: số luôn là hiện trạng. */
export const dashboardApi = {
  /** `from` / `to` dạng `YYYY-MM-DD` (giờ Việt Nam); vắng = 14 ngày đến hôm nay. Tối đa 366 ngày. */
  get(from?: string, to?: string): Promise<Dashboard> {
    return http.get<Dashboard>('/admin/dashboard', { params: { from, to } }).then((r) => r.data)
  },
}
