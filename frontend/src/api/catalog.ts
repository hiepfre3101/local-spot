import { http } from './http'
import type { Amenity, CategoryNode, City, HomeResponse } from '@/types/api'

/** openapi tag Catalog — công khai, có cache Redis ở backend. */
export const catalogApi = {
  /** Cây danh mục ≤ 2 cấp. */
  categories(): Promise<CategoryNode[]> {
    return http.get<CategoryNode[]>('/categories').then((r) => r.data)
  },

  amenities(): Promise<Amenity[]> {
    return http.get<Amenity[]>('/amenities').then((r) => r.data)
  },

  /** Thành phố có địa điểm đang hiện, nhiều địa điểm trước — bộ lọc khu vực P08. */
  cities(): Promise<City[]> {
    return http.get<City[]>('/cities').then((r) => r.data)
  },

  /** Trang chủ (FR-08): nổi bật, mới duyệt, danh mục, đánh giá nổi bật — một request. */
  home(): Promise<HomeResponse> {
    return http.get<HomeResponse>('/home').then((r) => r.data)
  },
}
