import type { GeoPoint } from '@/types/api'

export type TravelMode = 'drive' | 'walk'

export interface RouteResult {
  distanceM: number
  durationS: number
  /** Đường đi theo thứ tự từ điểm đi tới điểm đến. */
  path: GeoPoint[]
}

/**
 * Chỉ đường trong ứng dụng (P03, chốt 2026-10-13 — không mở trang ngoài). Gọi **OSRM** trên máy chủ cộng đồng của
 * OpenStreetMap (FOSSGIS, `routing.openstreetmap.de`): không cần key, cho phép gọi từ trình duyệt (CORS `*`), có hồ sơ
 * ô tô và đi bộ. Dùng `fetch` thay vì axios dùng chung — dịch vụ ngoài, không được gắn access token của LocalSpot.
 * Đổi máy chủ (vd. OSRM tự dựng khi deploy) bằng `VITE_ROUTING_URL`.
 *
 * Đánh đổi: máy chủ cộng đồng không cam kết SLA và giới hạn lưu lượng → giao diện luôn có phương án khi lỗi (khoảng
 * cách đường chim bay). Việt Nam chủ yếu đi xe máy — OSRM không có hồ sơ xe máy, hồ sơ ô tô là gần nhất (cùng đường
 * lớn, thời gian ước lượng có thể lệch).
 */
const PROFILES: Record<TravelMode, string> = {
  drive: 'routed-car/route/v1/driving',
  walk: 'routed-foot/route/v1/foot',
}

const TIMEOUT_MS = 12_000

export function routingBaseUrl(env: ImportMetaEnv = import.meta.env): string {
  return (env.VITE_ROUTING_URL?.trim() || 'https://routing.openstreetmap.de').replace(/\/+$/, '')
}

export function routeUrl(
  from: GeoPoint,
  to: GeoPoint,
  mode: TravelMode,
  base = routingBaseUrl(),
): string {
  // OSRM nhận "kinh độ,vĩ độ"
  const coords = `${from.lng},${from.lat};${to.lng},${to.lat}`
  return `${base}/${PROFILES[mode]}/${coords}?overview=full&geometries=geojson&alternatives=false&steps=false`
}

interface OsrmResponse {
  code: string
  routes?: { distance: number; duration: number; geometry: { coordinates: [number, number][] } }[]
}

export class RouteNotFoundError extends Error {}

/** Ném `RouteNotFoundError` khi không có đường (vd. qua biển); lỗi mạng / quá giờ ném lỗi thường. */
export async function fetchRoute(
  from: GeoPoint,
  to: GeoPoint,
  mode: TravelMode,
  signal?: AbortSignal,
): Promise<RouteResult> {
  const timeout = AbortSignal.timeout(TIMEOUT_MS)
  const response = await fetch(routeUrl(from, to, mode), {
    signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
    headers: { Accept: 'application/json' },
  })
  const body = (await response.json().catch(() => null)) as OsrmResponse | null
  const route = body?.routes?.[0]
  if (body?.code === 'NoRoute' || (response.ok && !route)) {
    throw new RouteNotFoundError('Không tìm được đường đi')
  }
  if (!response.ok || !route) {
    throw new Error(`Dịch vụ chỉ đường lỗi (${response.status})`)
  }
  return {
    distanceM: route.distance,
    durationS: route.duration,
    path: route.geometry.coordinates.map(([lng, lat]) => ({ lat, lng })),
  }
}

/** "8 phút", "1 giờ 5 phút" — làm tròn lên phút, tối thiểu 1 phút. */
export function formatDuration(seconds: number): string {
  const minutes = Math.max(1, Math.ceil(seconds / 60))
  if (minutes < 60) return `${minutes} phút`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours} giờ ${rest} phút` : `${hours} giờ`
}
