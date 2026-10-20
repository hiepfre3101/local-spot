/** Vị trí đo được để check-in: toạ độ + độ chính xác (m) từ Geolocation API. */
export interface MeasuredPosition {
  lat: number
  lng: number
  accuracyM: number
}

export type PositionFailure = 'denied' | 'unavailable' | 'timeout' | 'unsupported'

export class PositionError extends Error {
  constructor(readonly reason: PositionFailure) {
    super(reason)
    this.name = 'PositionError'
  }
}

/**
 * Đo vị trí **mới** với độ chính xác cao cho check-in (P12: tối đa 10 giây). Khác `usePlaceStore().locate()` của tìm
 * quanh tôi: không dùng lại vị trí cũ (`maximumAge: 0`) và cần `accuracy` để backend kiểm ≤ 100 m.
 */
export function measurePosition(timeoutMs = 10_000): Promise<MeasuredPosition> {
  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    return Promise.reject(new PositionError('unsupported'))
  }
  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (pos) =>
        resolve({
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
          accuracyM: pos.coords.accuracy,
        }),
      (error) =>
        reject(
          new PositionError(
            error.code === error.PERMISSION_DENIED
              ? 'denied'
              : error.code === error.TIMEOUT
                ? 'timeout'
                : 'unavailable',
          ),
        ),
      { enableHighAccuracy: true, timeout: timeoutMs, maximumAge: 0 },
    )
  })
}
