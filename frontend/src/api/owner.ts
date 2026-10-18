import { http } from './http'
import type {
  CursorPage,
  MyClaim,
  OpeningHour,
  OwnerReply,
  Photo,
  PlaceDetail,
  PlaceStats,
  PlaceSummary,
} from '@/types/api'

/**
 * openapi `PlaceUpdateRequest` (UC24) — ngữ nghĩa PATCH: trường vắng = giữ nguyên. Xoá mô tả / điện thoại / web bằng
 * chuỗi rỗng; `openingHours` / `amenityIds` là thay toàn bộ. Không có tên, vị trí, danh mục (đổi phải báo cáo
 * WRONG_INFO); giá chỉ đặt được, không xoá được (`null` = giữ nguyên). `version` lệch → 409.
 */
export interface PlaceUpdate {
  description?: string
  address?: string
  priceMin?: number
  priceMax?: number
  phone?: string
  website?: string
  openingHours?: OpeningHour[]
  amenityIds?: number[]
  version?: number
}

/** Thông tin kèm yêu cầu sở hữu (UC23) — điện thoại để kiểm duyệt viên gọi xác minh. */
export interface ClaimInput {
  contactPhone: string
  note?: string
}

/** openapi tag Owner — P09 (UC23–UC26). Quyền chủ kiểm ở backend (`@PreAuthorize` + chủ hiện tại). */
export const ownerApi = {
  /** Địa điểm tôi đang sở hữu (cần `place:update-own`). */
  places(): Promise<PlaceSummary[]> {
    return http.get<PlaceSummary[]>('/owner/places').then((r) => r.data)
  },

  update(placeId: number, update: PlaceUpdate): Promise<PlaceDetail> {
    return http.patch<PlaceDetail>(`/owner/places/${placeId}`, update).then((r) => r.data)
  },

  /** 201 kèm ảnh vừa nhận (PROCESSING — xử lý nền). Tối đa 30 ảnh / địa điểm. */
  addPhotos(placeId: number, photos: { blob: Blob; fileName: string }[]): Promise<Photo[]> {
    const form = new FormData()
    photos.forEach((p) => form.append('photos', p.blob, p.fileName))
    return http.post<Photo[]>(`/owner/places/${placeId}/photos`, form).then((r) => r.data)
  },

  deletePhoto(placeId: number, photoId: number): Promise<void> {
    return http.delete(`/owner/places/${placeId}/photos/${photoId}`).then(() => undefined)
  },

  /** Lượt xem / check-in theo ngày trong `[from, to]` (YYYY-MM-DD, giờ VN); vắng = 30 ngày đến hôm nay. */
  stats(placeId: number, from?: string, to?: string): Promise<PlaceStats> {
    return http
      .get<PlaceStats>(`/owner/places/${placeId}/stats`, { params: { from, to } })
      .then((r) => r.data)
  },

  /** Một PUT tạo hoặc sửa phản hồi (UC25). Chỉ review đang hiện công khai. */
  reply(reviewId: number, content: string): Promise<OwnerReply> {
    return http.put<OwnerReply>(`/reviews/${reviewId}/reply`, { content }).then((r) => r.data)
  },

  /** Xoá hẳn, idempotent. */
  deleteReply(reviewId: number): Promise<void> {
    return http.delete(`/reviews/${reviewId}/reply`).then(() => undefined)
  },

  /** Yêu cầu sở hữu của tôi, mới nhất trước — theo dõi kết quả duyệt. */
  myClaims(cursor?: string | null): Promise<CursorPage<MyClaim>> {
    return http
      .get<CursorPage<MyClaim>>('/me/claims', {
        params: { cursor: cursor ?? undefined, limit: 20 },
      })
      .then((r) => r.data)
  },

  /** UC23: multipart — phần `claim` là JSON, `evidence` 1–5 ảnh JPEG / PNG. Cần email đã xác thực. */
  claim(
    placeId: number,
    claim: ClaimInput,
    evidence: { blob: Blob; fileName: string }[],
  ): Promise<void> {
    const form = new FormData()
    form.append('claim', new Blob([JSON.stringify(claim)], { type: 'application/json' }))
    evidence.forEach((e) => form.append('evidence', e.blob, e.fileName))
    return http.post(`/places/${placeId}/claims`, form).then(() => undefined)
  },
}
