import { describe, expect, it } from 'vitest'

import { fullAddress } from '../format'

describe('fullAddress', () => {
  it('ghép thành phố sau địa chỉ', () => {
    expect(fullAddress({ address: '48 Cách Mạng Tháng Tám', city: 'TP. Hồ Chí Minh' })).toBe(
      '48 Cách Mạng Tháng Tám, TP. Hồ Chí Minh',
    )
  })

  it('địa chỉ đã kết thúc bằng thành phố thì không ghép lại (không phân biệt dấu / hoa thường)', () => {
    expect(fullAddress({ address: '12 Lê Lợi, Phường 3, Đà Lạt', city: 'Đà Lạt' })).toBe(
      '12 Lê Lợi, Phường 3, Đà Lạt',
    )
    expect(fullAddress({ address: '5 Trần Phú, da lat.', city: 'Đà Lạt' })).toBe(
      '5 Trần Phú, da lat.',
    )
  })

  it('thành phố chỉ nằm giữa địa chỉ vẫn ghép (vd. tên đường trùng tên thành phố)', () => {
    expect(fullAddress({ address: '10 Đường Huế', city: 'Hà Nội' })).toBe('10 Đường Huế, Hà Nội')
  })

  it('thiếu một trong hai thì hiện phần còn lại', () => {
    expect(fullAddress({ address: '3 Hai Bà Trưng', city: null })).toBe('3 Hai Bà Trưng')
    expect(fullAddress({ address: ' ', city: 'Huế' })).toBe('Huế')
  })
})
