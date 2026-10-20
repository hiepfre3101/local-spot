import { http } from './http'
import type { CollectionDetail, CollectionSummary } from '@/types/api'

/** openapi `CollectionRequest` — tạo và sửa dùng chung (PATCH gửi lại đủ tên / mô tả / công khai như form). */
export interface CollectionInput {
  name: string
  description: string | null
  isPublic: boolean
}

/**
 * openapi tag Collections (UC18). Bộ riêng tư / bộ của người khác → 404 `COLLECTION_NOT_FOUND` (không tiết lộ tồn tại);
 * trần 50 bộ / người, 100 địa điểm / bộ → 422 `COLLECTION_LIMIT_EXCEEDED`.
 */
export const collectionsApi = {
  /** Bộ của tôi, mới tạo trước. `placeId` → mỗi bộ kèm `containsPlace` (hộp "Lưu"). */
  mine(placeId?: number): Promise<CollectionSummary[]> {
    return http
      .get<CollectionSummary[]>('/me/collections', { params: { placeId } })
      .then((r) => r.data)
  },

  /** Bộ công khai, hoặc của chính người xem. */
  get(collectionId: number): Promise<CollectionDetail> {
    return http.get<CollectionDetail>(`/collections/${collectionId}`).then((r) => r.data)
  },

  /** Link chia sẻ — luôn là góc nhìn công khai, kể cả khi chủ tự mở. */
  shared(shareSlug: string): Promise<CollectionDetail> {
    return http
      .get<CollectionDetail>(`/shared/collections/${encodeURIComponent(shareSlug)}`)
      .then((r) => r.data)
  },

  create(input: CollectionInput): Promise<CollectionDetail> {
    return http.post<CollectionDetail>('/collections', input).then((r) => r.data)
  },

  /** Về riêng tư thu hồi link; công khai lại sinh link mới. */
  update(collectionId: number, input: CollectionInput): Promise<CollectionDetail> {
    return http.patch<CollectionDetail>(`/collections/${collectionId}`, input).then((r) => r.data)
  },

  remove(collectionId: number): Promise<void> {
    return http.delete(`/collections/${collectionId}`).then(() => undefined)
  },

  /** Idempotent: đã có → chỉ sửa ghi chú. Chỉ thêm được địa điểm đang hiện. */
  addPlace(collectionId: number, placeId: number, note?: string | null): Promise<void> {
    return http
      .put(
        `/collections/${collectionId}/places/${placeId}`,
        note === undefined ? undefined : { note },
      )
      .then(() => undefined)
  },

  removePlace(collectionId: number, placeId: number): Promise<void> {
    return http.delete(`/collections/${collectionId}/places/${placeId}`).then(() => undefined)
  },
}
