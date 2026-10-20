import { http } from './http'
import type { CheckIn } from '@/types/api'
import type { MeasuredPosition } from '@/utils/position'

/** Luật check-in của backend (openapi, chốt 2026-10-12) — hiển thị trong hộp thoại, kiểm thật ở máy chủ. */
export const CHECKIN_RADIUS_M = 200
export const CHECKIN_MAX_ACCURACY_M = 100

/** openapi tag CheckIns (UC16, FR-24). */
export const checkInsApi = {
  /**
   * 201 kèm khoảng cách thật. Lỗi theo `code`: `GPS_INACCURATE` / `CHECKIN_TOO_FAR` (422, problem có `distanceM`),
   * `CHECKIN_ALREADY_TODAY` / `SELF_ACTION_FORBIDDEN` (409), `EMAIL_NOT_VERIFIED` (403).
   */
  checkIn(placeId: number, position: MeasuredPosition): Promise<CheckIn> {
    return http
      .post<CheckIn>(`/places/${placeId}/check-in`, {
        lat: position.lat,
        lng: position.lng,
        accuracyM: position.accuracyM,
      })
      .then((r) => r.data)
  },
}
