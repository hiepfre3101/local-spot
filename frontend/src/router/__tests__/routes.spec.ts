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
})
