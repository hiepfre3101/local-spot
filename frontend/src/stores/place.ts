import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'

import type { GeoPoint } from '@/types/api'

export type LocateStatus = 'idle' | 'locating' | 'ready' | 'denied' | 'unavailable'

/** Vị trí cũ hơn 5 phút thì hỏi lại — người dùng có thể đã đi chỗ khác. */
const MAX_AGE_MS = 5 * 60_000

/**
 * Trạng thái phía client của module địa điểm (plan §2 — store `place`). Dữ liệu từ API nằm trong cache Vue Query, store
 * này chỉ giữ thứ không đến từ server:
 *
 * - **Vị trí người dùng** (UC09) — dùng chung giữa "Tìm quanh tôi" ở trang chủ và chip "Quanh tôi" ở P02; chỉ nằm trong
 *   bộ nhớ, không gửi lên URL (`near=me`) hay lưu lại — vị trí là dữ liệu cá nhân.
 * - **Địa điểm đang chọn** — hàng danh sách và ghim bản đồ được đánh dấu cùng lúc (P02).
 */
export const usePlaceStore = defineStore('place', () => {
  const location = shallowRef<GeoPoint | null>(null)
  const locateStatus = ref<LocateStatus>('idle')
  const activePlaceId = ref<number | null>(null)
  let locatedAt = 0
  let pending: Promise<GeoPoint | null> | null = null

  /** Hỏi vị trí (gộp các lời gọi đồng thời); trả `null` nếu bị từ chối / trình duyệt không hỗ trợ. */
  function locate(): Promise<GeoPoint | null> {
    if (location.value && Date.now() - locatedAt < MAX_AGE_MS) {
      return Promise.resolve(location.value)
    }
    if (pending) return pending
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      locateStatus.value = 'unavailable'
      return Promise.resolve(null)
    }
    locateStatus.value = 'locating'
    pending = new Promise<GeoPoint | null>((resolve) => {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          location.value = { lat: pos.coords.latitude, lng: pos.coords.longitude }
          locatedAt = Date.now()
          locateStatus.value = 'ready'
          resolve(location.value)
        },
        (error) => {
          locateStatus.value = error.code === error.PERMISSION_DENIED ? 'denied' : 'unavailable'
          resolve(null)
        },
        { enableHighAccuracy: false, timeout: 15_000, maximumAge: 60_000 },
      )
    }).finally(() => {
      pending = null
    })
    return pending
  }

  function setActive(placeId: number | null): void {
    activePlaceId.value = placeId
  }

  return { location, locateStatus, activePlaceId, locate, setActive }
})
