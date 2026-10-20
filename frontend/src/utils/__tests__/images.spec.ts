import { describe, expect, it } from 'vitest'

import { MAX_UPLOAD_BYTES, fitWithin, isUploadReady, jpegName } from '@/utils/images'
import { reviewSchema, todayInVietnam } from '@/validation/review'

describe('chuẩn bị ảnh tải lên', () => {
  it('JPEG / PNG ≤ 5 MB gửi nguyên; định dạng khác hoặc quá nặng phải chuyển', () => {
    expect(isUploadReady(new Blob(['x'], { type: 'image/jpeg' }))).toBe(true)
    expect(isUploadReady(new Blob(['x'], { type: 'image/png' }))).toBe(true)
    expect(isUploadReady(new Blob(['x'], { type: 'image/webp' }))).toBe(false)
    expect(
      isUploadReady(new Blob([new Uint8Array(MAX_UPLOAD_BYTES + 1)], { type: 'image/jpeg' })),
    ).toBe(false)
  })

  it('thu nhỏ giữ tỷ lệ theo cạnh dài, không phóng to ảnh nhỏ', () => {
    expect(fitWithin(8000, 6000, 4096)).toEqual({ width: 4096, height: 3072 })
    expect(fitWithin(3000, 9000, 4096)).toEqual({ width: 1365, height: 4096 })
    expect(fitWithin(800, 600, 4096)).toEqual({ width: 800, height: 600 })
  })

  it('đổi đuôi sang .jpg để khớp chữ ký file', () => {
    expect(jpegName('IMG_0012.HEIC')).toBe('IMG_0012.jpg')
    expect(jpegName('anh.chup.webp')).toBe('anh.chup.jpg')
    expect(jpegName('khongduoi')).toBe('khongduoi.jpg')
  })
})

describe('luật validate đánh giá', () => {
  it('"hôm nay" theo giờ Việt Nam, không theo múi giờ máy', () => {
    // 18:30 UTC ngày 16 = 01:30 sáng ngày 17 ở Việt Nam
    expect(todayInVietnam(new Date('2026-10-16T18:30:00Z'))).toBe('2026-10-17')
    expect(todayInVietnam(new Date('2026-10-16T16:59:59Z'))).toBe('2026-10-16')
  })

  it('nội dung tính sau khi bỏ khoảng trắng; ngày đến không ở tương lai', () => {
    const schema = reviewSchema('2026-10-17')
    const ok = { rating: 4, content: 'x'.repeat(20), visitedAt: '2026-10-17' }
    expect(schema.safeParse(ok).success).toBe(true)
    expect(schema.safeParse({ ...ok, content: `  ${'x'.repeat(19)}   ` }).success).toBe(false)
    expect(schema.safeParse({ ...ok, visitedAt: '2026-10-18' }).success).toBe(false)
    expect(schema.safeParse({ ...ok, rating: 6 }).success).toBe(false)
    expect(schema.safeParse({ ...ok, content: 'x'.repeat(5001) }).success).toBe(false)
  })
})
