import type { GeoPoint } from '@/types/api'

import { MAX_RADIUS, MIN_RADIUS } from './searchQuery'

const EARTH_RADIUS_M = 6_371_008.8

/** Khoảng cách mặt cầu (haversine) — cùng mô hình với `ST_Distance_Sphere` của MySQL ở backend. */
export function distanceM(a: GeoPoint, b: GeoPoint): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180
  const dLat = toRad(b.lat - a.lat)
  const dLng = toRad(b.lng - a.lng)
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)))
}

/**
 * Bán kính cho "Tìm trong vùng này": từ tâm tới cạnh **gần hơn** của khung bản đồ (vòng tròn nằm trọn trong vùng nhìn
 * thấy — không trả về địa điểm ngoài màn hình), làm tròn lên 100 m và kẹp trong 100 m – 20 km (U8).
 */
export function radiusForView(center: GeoPoint, north: number, east: number): number {
  const toTop = distanceM(center, { lat: north, lng: center.lng })
  const toSide = distanceM(center, { lat: center.lat, lng: east })
  const radius = Math.ceil(Math.min(toTop, toSide) / 100) * 100
  return Math.min(MAX_RADIUS, Math.max(MIN_RADIUS, radius))
}

/** "350 m", "1,2 km", "16 km" — số kiểu Việt Nam (README design system). */
export function formatDistance(meters: number): string {
  if (meters < 1000) return `${Math.max(1, Math.round(meters / 10) * 10)} m`
  const km = meters / 1000
  const text = km < 10 ? km.toFixed(1) : String(Math.round(km))
  return `${text.replace('.', ',').replace(/,0$/, '')} km`
}
