import { http } from './http'
import type {
  CheckIn,
  CollectionSummary,
  CursorPage,
  PlaceSummary,
  PublicProfile,
  Review,
  UserSummary,
} from '@/types/api'

function page(cursor?: string | null, limit = 10) {
  return { params: { cursor: cursor ?? undefined, limit } }
}

/** openapi tag Users + phần `/me/*` của trang cá nhân (P06 — UC18, UC20, UC21, UC11 theo dõi đề xuất). */
export const usersApi = {
  /** Hồ sơ công khai; gửi kèm token để nhận `followedByMe`. */
  profile(userId: number): Promise<PublicProfile> {
    return http.get<PublicProfile>(`/users/${userId}`).then((r) => r.data)
  },

  /** Review đã công khai của một người. */
  reviews(userId: number, cursor?: string | null): Promise<CursorPage<Review>> {
    return http
      .get<CursorPage<Review>>(`/users/${userId}/reviews`, page(cursor))
      .then((r) => r.data)
  },

  /** Bộ sưu tập công khai của một người. */
  collections(userId: number): Promise<CollectionSummary[]> {
    return http.get<CollectionSummary[]>(`/users/${userId}/collections`).then((r) => r.data)
  },

  followers(userId: number, cursor?: string | null): Promise<CursorPage<UserSummary>> {
    return http
      .get<CursorPage<UserSummary>>(`/users/${userId}/followers`, page(cursor, 20))
      .then((r) => r.data)
  },

  following(userId: number, cursor?: string | null): Promise<CursorPage<UserSummary>> {
    return http
      .get<CursorPage<UserSummary>>(`/users/${userId}/following`, page(cursor, 20))
      .then((r) => r.data)
  },

  /** Idempotent; tự theo dõi mình → 409 `SELF_ACTION_FORBIDDEN`. */
  follow(userId: number): Promise<void> {
    return http.put(`/users/${userId}/follow`).then(() => undefined)
  },

  unfollow(userId: number): Promise<void> {
    return http.delete(`/users/${userId}/follow`).then(() => undefined)
  },

  // ─── Chỉ chính chủ ────────────────────────────────────────────────────────

  /** Review của tôi — mọi trạng thái, kèm lý do bị từ chối. */
  myReviews(cursor?: string | null): Promise<CursorPage<Review>> {
    return http.get<CursorPage<Review>>('/me/reviews', page(cursor)).then((r) => r.data)
  },

  /** Bộ sưu tập của tôi — công khai lẫn riêng tư. */
  myCollections(): Promise<CollectionSummary[]> {
    return http.get<CollectionSummary[]>('/me/collections').then((r) => r.data)
  },

  /** Lịch sử check-in — không có bản công khai (riêng tư vị trí). */
  myCheckIns(cursor?: string | null): Promise<CursorPage<CheckIn>> {
    return http.get<CursorPage<CheckIn>>('/me/check-ins', page(cursor)).then((r) => r.data)
  },

  /** Địa điểm tôi đã đề xuất, mọi trạng thái (UC11 — theo dõi kết quả duyệt). */
  myPlaces(cursor?: string | null): Promise<CursorPage<PlaceSummary>> {
    return http.get<CursorPage<PlaceSummary>>('/me/places', page(cursor)).then((r) => r.data)
  },
}
