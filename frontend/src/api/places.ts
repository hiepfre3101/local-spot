import { http } from './http'
import type { CursorPage, PlaceDetail, PlaceSummary, Review, SearchPage } from '@/types/api'

/** Bộ lọc chung của `GET /places` và `GET /search` (FR-10). Trường rỗng = không lọc. */
export interface PlaceFilter {
  categoryId?: number
  amenityIds?: number[]
  priceMax?: number
  minRating?: number
  city?: string
}

export type PlaceSort = 'SCORE' | 'NEWEST' | 'MOST_REVIEWED'
export type SearchSort = 'RELEVANCE' | PlaceSort
export type ReviewSort = 'NEWEST' | 'HELPFUL'

export interface NearbyQuery {
  lat: number
  lng: number
  /** Mét, 100 – 20 000 (U8). */
  radius: number
  categoryId?: number
  limit?: number
}

/**
 * Tham số lọc theo đúng dạng backend nhận: `amenityIds` là danh sách ngăn cách bằng dấu phẩy (openapi `explode: false`)
 * — mặc định axios gửi `amenityIds[]=1&amenityIds[]=2`, Spring không đọc được.
 */
function filterParams(filter: PlaceFilter): Record<string, string | number | undefined> {
  return {
    categoryId: filter.categoryId,
    amenityIds: filter.amenityIds?.length ? filter.amenityIds.join(',') : undefined,
    priceMax: filter.priceMax,
    minRating: filter.minRating,
    city: filter.city || undefined,
  }
}

/** openapi tag Places / Search / Reviews — phần đọc công khai của module khám phá. */
export const placesApi = {
  list(
    filter: PlaceFilter,
    sort: PlaceSort,
    cursor?: string | null,
    limit = 20,
  ): Promise<CursorPage<PlaceSummary>> {
    return http
      .get<CursorPage<PlaceSummary>>('/places', {
        params: { ...filterParams(filter), sort, cursor: cursor ?? undefined, limit },
      })
      .then((r) => r.data)
  },

  /** Tìm tiếng Việt (Meilisearch, fallback theo tên khi lỗi — `degraded`). Không lọc khoảng cách (UC08 2a → UC09). */
  search(
    q: string,
    filter: PlaceFilter,
    sort: SearchSort,
    cursor?: string | null,
    limit = 20,
  ): Promise<SearchPage> {
    return http
      .get<SearchPage>('/search', {
        params: { q, ...filterParams(filter), sort, cursor: cursor ?? undefined, limit },
      })
      .then((r) => r.data)
  },

  /** Trong bán kính, gần nhất trước, kèm `distanceM` (UC09). Không phân trang — tối đa `limit` (≤ 50). */
  nearby(query: NearbyQuery): Promise<PlaceSummary[]> {
    return http.get<PlaceSummary[]>('/places/nearby', { params: query }).then((r) => r.data)
  },

  /** Chi tiết theo slug; gửi kèm token (nếu có) để nhận `myReviewId`, `claimable`. */
  detail(slug: string): Promise<PlaceDetail> {
    return http.get<PlaceDetail>(`/places/${encodeURIComponent(slug)}`).then((r) => r.data)
  },

  /** Review PUBLISHED của địa điểm, keyset theo thứ tự đã chọn. */
  reviews(
    placeId: number,
    options: { sort: ReviewSort; rating?: number; cursor?: string | null; limit?: number },
  ): Promise<CursorPage<Review>> {
    return http
      .get<CursorPage<Review>>(`/places/${placeId}/reviews`, {
        params: {
          sort: options.sort,
          rating: options.rating,
          cursor: options.cursor ?? undefined,
          limit: options.limit ?? 10,
        },
      })
      .then((r) => r.data)
  },
}
