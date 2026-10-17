import { http } from './http'
import type { Banner, BannerSlot } from '@/types/api'

/** openapi `/admin/banners` — ảnh trang chủ (UC34). Cần quyền `banner:manage`. */
export const bannersApi = {
  /** Đủ 3 ô, kể cả ô chưa có ảnh. */
  list(): Promise<Banner[]> {
    return http.get<Banner[]>('/admin/banners').then((r) => r.data)
  },

  /** Thay ảnh (tạo nếu chưa có). `altText` bỏ trống = giữ mô tả cũ. */
  replaceImage(slot: BannerSlot, image: Blob, fileName: string, altText?: string): Promise<Banner> {
    const form = new FormData()
    form.append('image', image, fileName)
    if (altText !== undefined) {
      form.append('altText', altText)
    }
    return http.put<Banner>(`/admin/banners/${slot}/image`, form).then((r) => r.data)
  },

  /** Sửa mô tả / bật tắt — `version` lệch → 409 `CONCURRENT_MODIFICATION`. */
  update(
    slot: BannerSlot,
    change: { altText?: string; active?: boolean; version: number },
  ): Promise<Banner> {
    return http.patch<Banner>(`/admin/banners/${slot}`, change).then((r) => r.data)
  },

  /** Gỡ ảnh — ô về hình minh hoạ mặc định. */
  remove(slot: BannerSlot): Promise<void> {
    return http.delete(`/admin/banners/${slot}`).then(() => undefined)
  },
}
