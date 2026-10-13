import { describe, expect, it } from 'vitest'

import {
  DEFAULT_RADIUS,
  effectiveSort,
  emptySearchState,
  parseSearchQuery,
  searchMode,
  toSearchQuery,
} from '../searchQuery'

describe('parseSearchQuery', () => {
  it('đọc đủ bộ lọc của sitemap §2.1', () => {
    const state = parseSearchQuery({
      q: '  phở bò ',
      category: 'pho-bun',
      minRating: '4.5',
      price: '100000',
      amenities: 'wifi,may-lanh,wifi',
      near: '21.0285,105.8542',
      radius: '5000',
      sort: 'newest',
    })
    expect(state).toEqual({
      q: 'phở bò',
      category: 'pho-bun',
      minRating: 4.5,
      price: 100000,
      amenities: ['wifi', 'may-lanh'],
      near: { lat: 21.0285, lng: 105.8542 },
      radius: 5000,
      sort: 'NEWEST',
    })
  })

  it('bỏ qua giá trị sai thay vì ném lỗi (URL gõ tay)', () => {
    const state = parseSearchQuery({
      category: 'Phở Bún!',
      minRating: '9',
      price: '-5',
      amenities: ',,<script>,wifi',
      near: '200,10',
      radius: '50',
      sort: 'DROP',
    })
    expect(state).toEqual({ ...emptySearchState(), amenities: ['wifi'] })
  })

  it('`near=me` là vị trí người dùng; bán kính mặc định 2 km', () => {
    const state = parseSearchQuery({ near: 'me' })
    expect(state.near).toBe('me')
    expect(state.radius).toBe(DEFAULT_RADIUS)
  })

  it('cắt từ khóa ở 100 ký tự (giới hạn của /search)', () => {
    expect(parseSearchQuery({ q: 'a'.repeat(150) }).q).toHaveLength(100)
  })
})

describe('toSearchQuery', () => {
  it('bỏ giá trị mặc định cho URL gọn', () => {
    expect(toSearchQuery(emptySearchState())).toEqual({})
    expect(toSearchQuery({ ...emptySearchState(), near: 'me', radius: DEFAULT_RADIUS })).toEqual({
      near: 'me',
    })
  })

  it('đi vòng parse → serialize giữ nguyên trạng thái', () => {
    const query = {
      q: 'cafe',
      category: 'ca-phe-do-uong',
      minRating: '4',
      price: '50000',
      amenities: 'wifi,may-lanh',
      sort: 'SCORE',
    }
    expect(toSearchQuery(parseSearchQuery(query))).toEqual(query)
  })

  it('làm tròn tọa độ tới 5 chữ số; bán kính chỉ ghi khi đang tìm quanh vị trí', () => {
    expect(
      toSearchQuery({
        ...emptySearchState(),
        near: { lat: 21.028512345, lng: 105.854198765 },
        radius: 3000,
      }),
    ).toEqual({ near: '21.02851,105.8542', radius: '3000' })
    expect(toSearchQuery({ ...emptySearchState(), radius: 3000 })).toEqual({})
  })

  it('không ghi thứ tự mặc định của chế độ, và bỏ thứ tự không hợp lệ với chế độ', () => {
    expect(toSearchQuery({ ...emptySearchState(), q: 'phở', sort: 'RELEVANCE' })).toEqual({
      q: 'phở',
    })
    expect(toSearchQuery({ ...emptySearchState(), sort: 'SCORE' })).toEqual({})
    // RELEVANCE chỉ có khi có từ khóa
    expect(toSearchQuery({ ...emptySearchState(), sort: 'RELEVANCE' })).toEqual({})
  })
})

describe('chế độ & thứ tự', () => {
  it('quanh vị trí thắng từ khóa (UC09 chỉ lọc danh mục + bán kính)', () => {
    expect(searchMode({ ...emptySearchState(), q: 'phở', near: 'me' })).toBe('nearby')
    expect(searchMode({ ...emptySearchState(), q: 'phở' })).toBe('search')
    expect(searchMode(emptySearchState())).toBe('browse')
  })

  it('thứ tự mặc định theo chế độ', () => {
    expect(effectiveSort(emptySearchState())).toBe('SCORE')
    expect(effectiveSort({ ...emptySearchState(), q: 'phở' })).toBe('RELEVANCE')
    expect(effectiveSort({ ...emptySearchState(), near: 'me', sort: 'NEWEST' })).toBeNull()
    expect(effectiveSort({ ...emptySearchState(), sort: 'RELEVANCE' })).toBe('SCORE')
  })
})
