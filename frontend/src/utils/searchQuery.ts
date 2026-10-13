import type { LocationQuery, LocationQueryRaw } from 'vue-router'

import type { PlaceSort, SearchSort } from '@/api/places'
import type { GeoPoint } from '@/types/api'

/**
 * Trạng thái trang tìm kiếm P02 — **URL là nguồn sự thật** (sitemap §2.1: `q`, `category`, `minRating`, `price`,
 * `amenities`, `near`, `sort`, thêm `radius`): chia sẻ được liên kết, nút Quay lại hoạt động đúng. Hàm thuần, không phụ
 * thuộc router để test được; giá trị không hợp lệ trong URL bị bỏ qua (không ném lỗi cho người gõ tay URL).
 */
export interface SearchState {
  q: string
  /** Slug danh mục (URL dễ đọc) — đổi sang id khi gọi API. */
  category: string | null
  minRating: number | null
  /** Ngân sách tối đa (VND) → `priceMax`. */
  price: number | null
  /** Slug tiện ích, phải có đủ tất cả (AND). */
  amenities: string[]
  /** `me` = vị trí người dùng (UC09); tọa độ = "Tìm trong vùng này" trên bản đồ. */
  near: 'me' | GeoPoint | null
  /** Mét (U8: 100 m – 20 km). */
  radius: number
  sort: SearchSort | null
}

/** Ba cách lấy kết quả: duyệt (`/places`), tìm theo từ khóa (`/search`), quanh vị trí (`/places/nearby`). */
export type SearchMode = 'browse' | 'search' | 'nearby'

export const DEFAULT_RADIUS = 2000
export const MIN_RADIUS = 100
export const MAX_RADIUS = 20_000
export const RADIUS_OPTIONS = [1000, 2000, 5000, 10_000, 20_000] as const
export const MIN_RATING_OPTIONS = [3, 3.5, 4, 4.5] as const
export const PRICE_OPTIONS = [50_000, 100_000, 200_000, 500_000] as const
/** Giới hạn `q` của `/search` (openapi maxLength). */
export const MAX_QUERY_LENGTH = 100

const BROWSE_SORTS: readonly PlaceSort[] = ['SCORE', 'NEWEST', 'MOST_REVIEWED']
const SEARCH_SORTS: readonly SearchSort[] = ['RELEVANCE', ...BROWSE_SORTS]

export function emptySearchState(): SearchState {
  return {
    q: '',
    category: null,
    minRating: null,
    price: null,
    amenities: [],
    near: null,
    radius: DEFAULT_RADIUS,
    sort: null,
  }
}

/** "Quanh tôi" chỉ lọc theo danh mục + bán kính (API UC09) — từ khóa và bộ lọc khác bị tắt trong chế độ này. */
export function searchMode(state: SearchState): SearchMode {
  if (state.near) return 'nearby'
  return state.q ? 'search' : 'browse'
}

export function sortOptions(mode: SearchMode): readonly SearchSort[] {
  if (mode === 'search') return SEARCH_SORTS
  return mode === 'browse' ? BROWSE_SORTS : []
}

/** Thứ tự thực dùng: thứ tự trong URL nếu hợp lệ với chế độ hiện tại, ngược lại mặc định của chế độ. */
export function effectiveSort(state: SearchState): SearchSort | null {
  const mode = searchMode(state)
  const allowed = sortOptions(mode)
  if (state.sort && allowed.includes(state.sort)) return state.sort
  return allowed[0] ?? null
}

export function parseSearchQuery(query: LocationQuery): SearchState {
  const state = emptySearchState()
  state.q = first(query.q).trim().slice(0, MAX_QUERY_LENGTH)
  state.category = slugOrNull(first(query.category))
  state.minRating = numberIn(first(query.minRating), 1, 5)
  state.price = integerIn(first(query.price), 0, Number.MAX_SAFE_INTEGER)
  state.amenities = [
    ...new Set(
      first(query.amenities)
        .split(',')
        .map((s) => slugOrNull(s.trim()))
        .filter((s): s is string => s !== null),
    ),
  ]
  state.near = parseNear(first(query.near))
  state.radius = integerIn(first(query.radius), MIN_RADIUS, MAX_RADIUS) ?? DEFAULT_RADIUS
  const sort = first(query.sort).toUpperCase()
  state.sort = (SEARCH_SORTS as readonly string[]).includes(sort) ? (sort as SearchSort) : null
  return state
}

/** Ngược lại của {@link parseSearchQuery}; bỏ giá trị mặc định để URL ngắn gọn. */
export function toSearchQuery(state: SearchState): LocationQueryRaw {
  const query: LocationQueryRaw = {}
  if (state.q.trim()) query.q = state.q.trim().slice(0, MAX_QUERY_LENGTH)
  if (state.category) query.category = state.category
  if (state.minRating !== null) query.minRating = String(state.minRating)
  if (state.price !== null) query.price = String(state.price)
  if (state.amenities.length) query.amenities = state.amenities.join(',')
  if (state.near === 'me') query.near = 'me'
  else if (state.near) query.near = `${round5(state.near.lat)},${round5(state.near.lng)}`
  if (state.near && state.radius !== DEFAULT_RADIUS) query.radius = String(state.radius)
  const mode = searchMode(state)
  if (state.sort && state.sort !== sortOptions(mode)[0] && sortOptions(mode).includes(state.sort)) {
    query.sort = state.sort
  }
  return query
}

function first(value: LocationQuery[string] | undefined): string {
  const v = Array.isArray(value) ? value[0] : value
  return typeof v === 'string' ? v : ''
}

function slugOrNull(value: string): string | null {
  return /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value) && value.length <= 80 ? value : null
}

function numberIn(value: string, min: number, max: number): number | null {
  if (value === '') return null
  const n = Number(value)
  return Number.isFinite(n) && n >= min && n <= max ? n : null
}

function integerIn(value: string, min: number, max: number): number | null {
  const n = numberIn(value, min, max)
  return n !== null && Number.isInteger(n) ? n : null
}

function parseNear(value: string): SearchState['near'] {
  if (value === 'me') return 'me'
  const parts = value.split(',')
  if (parts.length !== 2) return null
  const lat = numberIn(parts[0]!.trim(), -90, 90)
  const lng = numberIn(parts[1]!.trim(), -180, 180)
  return lat !== null && lng !== null ? { lat, lng } : null
}

/** 5 chữ số thập phân ≈ 1 m — đủ cho tâm vùng tìm, URL không dài vô ích. */
function round5(value: number): number {
  return Math.round(value * 1e5) / 1e5
}
