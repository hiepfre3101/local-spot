import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { routes } from '../index'

/**
 * Khớp URL thật với bảng route — điều hướng bằng tên (RouterLink) không kiểm mẫu tham số, nên lỗi mẫu chỉ lộ ra khi
 * trình duyệt mở lại URL (nút Quay lại, F5, link chia sẻ).
 */
function resolve(path: string) {
  return createRouter({ history: createMemoryHistory(), routes }).resolve(path)
}

describe('bảng route', () => {
  it('trang cá nhân: URL số kèm ?tab khớp route profile, id là số', () => {
    const route = resolve('/users/16?tab=badges')
    expect(route.name).toBe('profile')
    expect(route.params.id).toBe('16')
    expect(route.query.tab).toBe('badges')
  })

  it('trang cá nhân: id không phải số → trang không tìm thấy', () => {
    expect(resolve('/users/abc').name).toBe('not-found')
  })

  it('các trang công khai chính khớp đúng route khi mở bằng URL', () => {
    expect(resolve('/places/pho-thin-ha-noi').name).toBe('place')
    expect(resolve('/places/pho-thin-ha-noi/review').name).toBe('write-review')
    expect(resolve('/propose').name).toBe('propose')
    expect(resolve('/search?q=pho').name).toBe('search')
  })

  it('chủ địa điểm: /owner (kèm ?place) và /owner/places/:id (số) kèm ?tab', () => {
    expect(resolve('/owner?place=pho-thin-ha-noi').name).toBe('owner')
    const managed = resolve('/owner/places/5?tab=reviews')
    expect(managed.name).toBe('owner-place')
    expect(managed.params.id).toBe('5')
    expect(resolve('/owner/places/abc').name).toBe('not-found')
  })

  it('bộ sưu tập: /collections/:id (số) và link chia sẻ /c/:shareSlug', () => {
    expect(resolve('/collections/7').name).toBe('collection')
    expect(resolve('/collections/abc').name).toBe('not-found')
    const shared = resolve('/c/quan-an-ha-noi-x7k2qa')
    expect(shared.name).toBe('shared-collection')
    expect(shared.params.shareSlug).toBe('quan-an-ha-noi-x7k2qa')
  })

  it('kiểm duyệt: hàng chờ + 4 màn xử lý theo sitemap §2.3, tham số sai → không tìm thấy', () => {
    expect(resolve('/admin/queue?type=REVIEW').name).toBe('admin-queue')
    expect(resolve('/admin/moderation/places/12').name).toBe('admin-moderation-place')
    expect(resolve('/admin/moderation/reviews/9').name).toBe('admin-moderation-review')
    expect(resolve('/admin/moderation/claims/3').name).toBe('admin-moderation-claim')
    const report = resolve('/admin/moderation/reports/COMMENT/44')
    expect(report.name).toBe('admin-moderation-report')
    expect(report.params).toEqual({ targetType: 'COMMENT', targetId: '44' })
    expect(resolve('/admin/moderation/reports/PHOTO/44').name).toBe('not-found')
    expect(resolve('/admin/moderation/places/abc').name).toBe('not-found')
  })
})
