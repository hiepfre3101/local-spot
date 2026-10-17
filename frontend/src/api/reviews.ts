import { http } from './http'
import type { Photo, Review } from '@/types/api'

/** openapi `ReviewCreateRequest` — `visitedAt` dạng `YYYY-MM-DD`, ≤ hôm nay theo giờ Việt Nam. */
export interface ReviewInput {
  rating: number
  content: string
  visitedAt: string
}

/** Ảnh gửi kèm: Blob (đã cắt / chuyển JPEG ở trình duyệt) + tên tệp — backend kiểm cả đuôi tệp lẫn chữ ký file. */
export interface PhotoUpload {
  blob: Blob
  fileName: string
}

function photosForm(photos: PhotoUpload[], form = new FormData()): FormData {
  photos.forEach((photo) => form.append('photos', photo.blob, photo.fileName))
  return form
}

/** openapi tag Reviews — phần ghi của module đánh giá (UC12, UC13, FR-19). */
export const reviewsApi = {
  /** Tác giả: review của mình ở mọi trạng thái; người khác: chỉ review đang hiện (404). */
  get(reviewId: number): Promise<Review> {
    return http.get<Review>(`/reviews/${reviewId}`).then((r) => r.data)
  },

  /**
   * Một request multipart (S2): phần `review` là JSON, phần `photos` tùy chọn. 201 kèm `status` PUBLISHED (đăng ngay)
   * hoặc PENDING (chờ duyệt — trust < 30 / cờ IP).
   */
  create(placeId: number, review: ReviewInput, photos: PhotoUpload[]): Promise<Review> {
    const form = new FormData()
    form.append('review', new Blob([JSON.stringify(review)], { type: 'application/json' }))
    return http
      .post<Review>(`/places/${placeId}/reviews`, photosForm(photos, form))
      .then((r) => r.data)
  },

  /** `version` lệch → 409 `CONCURRENT_MODIFICATION`; review bị ẩn → 409 `REVIEW_LOCKED`. */
  update(reviewId: number, review: ReviewInput & { version: number }): Promise<Review> {
    return http.patch<Review>(`/reviews/${reviewId}`, review).then((r) => r.data)
  },

  /** Xóa mềm — không viết lại được cho địa điểm này (D2). */
  remove(reviewId: number): Promise<void> {
    return http.delete(`/reviews/${reviewId}`).then(() => undefined)
  },

  addPhotos(reviewId: number, photos: PhotoUpload[]): Promise<Photo[]> {
    return http.post<Photo[]>(`/reviews/${reviewId}/photos`, photosForm(photos)).then((r) => r.data)
  },

  deletePhoto(reviewId: number, photoId: number): Promise<void> {
    return http.delete(`/reviews/${reviewId}/photos/${photoId}`).then(() => undefined)
  },
}
