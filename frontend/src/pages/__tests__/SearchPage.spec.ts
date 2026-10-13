import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { PlaceSummary } from '@/types/api'

import SearchPage from '../SearchPage.vue'

const list = vi.fn()
const search = vi.fn()
const nearby = vi.fn()

vi.mock('@/api/places', () => ({
  placesApi: {
    list: (...args: unknown[]) => list(...args),
    search: (...args: unknown[]) => search(...args),
    nearby: (...args: unknown[]) => nearby(...args),
  },
}))
vi.mock('@/api/catalog', () => ({
  catalogApi: {
    categories: () =>
      Promise.resolve([
        {
          id: 1,
          name: 'Quán ăn',
          slug: 'quan-an',
          icon: null,
          children: [{ id: 11, name: 'Phở & bún', slug: 'pho-bun', icon: null, children: [] }],
        },
        { id: 2, name: 'Cà phê', slug: 'ca-phe-do-uong', icon: null, children: [] },
      ]),
    amenities: () =>
      Promise.resolve([
        { id: 7, name: 'Wi-Fi miễn phí', slug: 'wifi', icon: null },
        { id: 8, name: 'Máy lạnh', slug: 'may-lanh', icon: null },
      ]),
  },
}))
// Leaflet cần layout thật — bản đồ thay bằng khung rỗng, kiểm ghim ở chạy thật
vi.mock('@/components/place/PlaceMap.vue', () => ({
  __esModule: true,
  default: { name: 'PlaceMap', props: ['places'], render: () => null },
}))

function place(id: number, name: string, extra: Partial<PlaceSummary> = {}): PlaceSummary {
  return {
    id,
    slug: `place-${id}`,
    name,
    category: { id: 11, name: 'Phở & bún', slug: 'pho-bun' },
    address: '1 Phố Huế',
    location: { lat: 21.03, lng: 105.85 },
    coverPhoto: null,
    priceMin: 50000,
    priceMax: 80000,
    bayesianScore: 4.62,
    avgRating: 4.8,
    reviewCount: 25,
    status: 'APPROVED',
    distanceM: null,
    tags: ['Bình dân', 'Gia truyền', 'Ăn sáng', 'Đông khách'],
    ...extra,
  }
}

async function mountAt(path: string) {
  const Empty = { render: () => null }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Empty },
      { path: '/search', name: 'search', component: SearchPage },
      { path: '/places/:slug', name: 'place', component: Empty },
    ],
  })
  await router.push(path)
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(SearchPage, {
    global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return { wrapper, router }
}

function mockGeolocation(result: 'ok' | 'denied') {
  vi.stubGlobal('navigator', {
    ...navigator,
    geolocation: {
      getCurrentPosition: (ok: PositionCallback, fail: PositionErrorCallback) => {
        if (result === 'ok') {
          ok({ coords: { latitude: 21.0285, longitude: 105.8542 } } as GeolocationPosition)
        } else {
          fail({ code: 1, PERMISSION_DENIED: 1 } as GeolocationPositionError)
        }
      },
    },
  })
}

describe('SearchPage (P02)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    list.mockReset().mockResolvedValue({ items: [place(1, 'Phở Thìn')], nextCursor: null })
    search
      .mockReset()
      .mockResolvedValue({ items: [place(2, 'Phở Bát Đàn')], nextCursor: null, degraded: false })
    nearby.mockReset().mockResolvedValue([place(3, 'Bún chả Hương Liên', { distanceM: 350 })])
  })
  afterEach(() => vi.unstubAllGlobals())

  it('duyệt: slug trên URL đổi thành id, gọi /places với bộ lọc và thứ tự mặc định SCORE', async () => {
    const { wrapper } = await mountAt(
      '/search?category=pho-bun&minRating=4&amenities=wifi,may-lanh&price=100000',
    )

    await vi.waitFor(() => expect(list).toHaveBeenCalled())
    expect(list).toHaveBeenLastCalledWith(
      { categoryId: 11, amenityIds: [7, 8], priceMax: 100000, minRating: 4 },
      'SCORE',
      null,
      20,
    )
    expect(search).not.toHaveBeenCalled()
    await flushPromises()
    expect(wrapper.text()).toContain('Phở Thìn')
    expect(wrapper.find('[role="img"]').attributes('aria-label')).toBe(
      '4,6 trên 5 điểm, 25 đánh giá',
    )
    // SpotCard / hàng: tối đa 3 thẻ
    expect(wrapper.findAll('.ls-tags li')).toHaveLength(3)
  })

  it('có từ khóa: gọi /search (RELEVANCE) và báo chế độ dự phòng', async () => {
    search.mockResolvedValue({ items: [place(2, 'Phở Bát Đàn')], nextCursor: null, degraded: true })
    const { wrapper } = await mountAt('/search?q=pho%20bat%20dan')

    await vi.waitFor(() => expect(search).toHaveBeenCalled())
    expect(search.mock.calls[0]![0]).toBe('pho bat dan')
    expect(search.mock.calls[0]![2]).toBe('RELEVANCE')
    await flushPromises()
    expect(wrapper.text()).toContain('Kết quả cho “pho bat dan”')
    expect(wrapper.text()).toContain('chế độ dự phòng')
  })

  it('quanh tôi: hỏi vị trí rồi gọi /places/nearby chỉ với danh mục + bán kính; tắt bộ lọc khác', async () => {
    mockGeolocation('ok')
    const { wrapper } = await mountAt('/search?near=me&category=quan-an&minRating=4&radius=5000')

    await vi.waitFor(() => expect(nearby).toHaveBeenCalled())
    expect(nearby).toHaveBeenLastCalledWith({
      lat: 21.0285,
      lng: 105.8542,
      radius: 5000,
      categoryId: 1,
      limit: 50,
    })
    expect(list).not.toHaveBeenCalled()
    await flushPromises()
    expect(wrapper.get('input[type="search"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('select[aria-label="Điểm tối thiểu"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('chỉ lọc theo danh mục và bán kính')
    expect(wrapper.text()).toContain('1 địa điểm quanh bạn · 5 km')
    expect(wrapper.text()).toContain('350 m')
  })

  it('bị chặn quyền vị trí: báo rõ, không gọi API, cho tắt "Quanh tôi"', async () => {
    mockGeolocation('denied')
    const { wrapper, router } = await mountAt('/search?near=me')

    await flushPromises()
    expect(nearby).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Bạn đã chặn quyền vị trí')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Tắt "Quanh tôi"')!
      .trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.near).toBeUndefined()
  })

  it('bấm chip danh mục ghi lên URL (nguồn sự thật) và tải lại với id mới', async () => {
    const { wrapper, router } = await mountAt('/search')
    await vi.waitFor(() => expect(list).toHaveBeenCalled())

    await wrapper
      .findAll('button.ls-fchip')
      .find((b) => b.text() === 'Cà phê')!
      .trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ category: 'ca-phe-do-uong' })
    await vi.waitFor(() =>
      expect(list).toHaveBeenLastCalledWith(
        expect.objectContaining({ categoryId: 2 }),
        'SCORE',
        null,
        20,
      ),
    )
  })

  it('không có kết quả: gợi ý bỏ bớt bộ lọc', async () => {
    list.mockResolvedValue({ items: [], nextCursor: null })
    const { wrapper } = await mountAt('/search?minRating=4.5')
    await vi.waitFor(() => expect(wrapper.text()).toContain('Không tìm thấy địa điểm nào'))
    expect(wrapper.text()).toContain('Xoá bộ lọc')
  })
})
