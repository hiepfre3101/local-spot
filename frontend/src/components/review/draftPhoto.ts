/** Ảnh người dùng vừa chọn, chưa tải lên: tệp đã chuẩn bị (JPEG / PNG đạt giới hạn) + URL xem trước. */
export interface DraftPhoto {
  key: string
  file: File
  url: string
}

let sequence = 0

export function draftOf(file: File): DraftPhoto {
  sequence += 1
  return { key: `draft-${sequence}`, file, url: URL.createObjectURL(file) }
}

export function releaseDraft(draft: DraftPhoto): void {
  URL.revokeObjectURL(draft.url)
}
