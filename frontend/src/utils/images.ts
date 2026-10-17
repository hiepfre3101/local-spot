/**
 * Chuẩn bị ảnh ở trình duyệt trước khi tải lên (FR-19, chốt 2026-10-06): backend chỉ nhận JPEG / PNG (chữ ký file + đuôi
 * tệp khớp), ≤ 5 MB, ≤ 40 MP. Ảnh đạt sẵn thì gửi nguyên; còn lại (WebP, HEIC trên Safari, ảnh quá nặng) vẽ lại lên
 * canvas và xuất JPEG — cạnh dài tối đa {@link MAX_EDGE}, đủ cho bản large 1920 px mà backend sinh ra.
 */
export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024
export const MAX_EDGE = 4096
const JPEG_QUALITY = 0.88
const ACCEPTED = new Set(['image/jpeg', 'image/png'])

export class ImageDecodeError extends Error {
  constructor(fileName: string) {
    super(`Không đọc được ảnh "${fileName}". Hãy chọn ảnh JPG hoặc PNG.`)
    this.name = 'ImageDecodeError'
  }
}

/** Ảnh có thể gửi nguyên không cần vẽ lại. */
export function isUploadReady(file: Blob): boolean {
  return ACCEPTED.has(file.type) && file.size <= MAX_UPLOAD_BYTES
}

/** Kích thước sau khi thu nhỏ giữ tỷ lệ để cạnh dài ≤ `maxEdge` (không phóng to). */
export function fitWithin(
  width: number,
  height: number,
  maxEdge = MAX_EDGE,
): { width: number; height: number } {
  const scale = Math.min(1, maxEdge / Math.max(width, height))
  return { width: Math.round(width * scale), height: Math.round(height * scale) }
}

/** Đổi đuôi tệp sang `.jpg` — backend kiểm đuôi khớp chữ ký file. */
export function jpegName(fileName: string): string {
  const base = fileName.replace(/\.[^./\\]+$/, '') || 'anh'
  return `${base}.jpg`
}

export function canvasToJpeg(canvas: HTMLCanvasElement, quality = JPEG_QUALITY): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('Không xuất được ảnh.'))),
      'image/jpeg',
      quality,
    )
  })
}

/** Vẽ lại ảnh thành JPEG (nền trắng cho ảnh PNG trong suốt — JPEG không có kênh alpha). */
export async function toJpeg(file: File): Promise<File> {
  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    throw new ImageDecodeError(file.name)
  }
  const size = fitWithin(bitmap.width, bitmap.height)
  const canvas = document.createElement('canvas')
  canvas.width = size.width
  canvas.height = size.height
  const context = canvas.getContext('2d')
  if (!context) {
    bitmap.close()
    throw new ImageDecodeError(file.name)
  }
  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, size.width, size.height)
  context.drawImage(bitmap, 0, 0, size.width, size.height)
  bitmap.close()
  return new File([await canvasToJpeg(canvas)], jpegName(file.name), { type: 'image/jpeg' })
}

/** Ảnh sẵn sàng tải lên: gửi nguyên nếu đạt, còn lại chuyển JPEG. */
export async function prepareImage(file: File): Promise<File> {
  return isUploadReady(file) ? file : toJpeg(file)
}
