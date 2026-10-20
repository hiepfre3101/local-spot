import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import type { PlaceDetail, PlaceStats, Review } from '@/types/api'

import OwnerPlacePage from '../owner/OwnerPlacePage.vue'

const owner = {
  places: vi.fn(),
  stats: vi.fn(),
  update: vi.fn(),
  reply: vi.fn(),
  deleteReply: vi.fn(),
  addPhotos: vi.fn(),
  deletePhoto: vi.fn(),
}
vi.mock('@/api/owner', () => ({
  ownerApi: new Proxy(
    {},
    {
      get:
        (_, name: string) =>
        (...args: unknown[]) =>
          owner[name as keyof typeof owner](...args),
    },
  ),
}))
const places = { detail: vi.fn(), reviews: vi.fn() }
vi.mock('@/api/places', () => ({
  placesApi: {
    detail: (...a: unknown[]) => places.detail(...a),
    reviews: (...a: unknown[]) => places.reviews(...a),
  },
}))
vi.mock('@/api/catalog', () => ({
  catalogApi: {
    categories: () => Promise.resolve([]),
    amenities: () =>
      Promise.resolve([
        { id: 7, name: 'Wi-Fi miễn phí', slug: 'wifi', icon: null },
        { id: 8, name: 'Chỗ gửi xe', slug: 'gui-xe', icon: null },
      ]),
  },
}))
vi.mock('@/plugins/toast', () => ({
  useNotify: () => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }),
}))

const PLACE: PlaceDetail = {
  id: 5,
  slug: 'pho-thin-ha-noi',
  name: 'Phở Thìn',
  category: { id: 11, name: 'Phở & bún', slug: 'pho-bun' },
  address: '13 Lò Đúc',
  location: { lat: 21.0175, lng: 105.8523 },
  coverPhoto: null,
  priceMin: 50000,
  priceMax: 80000,
  bayesianScore: 4.412,
  avgRating: 4.6,
  reviewCount: 10,
  status: 'APPROVED',
  distanceM: null,
  tags: [],
  description: 'Phở bò tái lăn',
  city: 'Hà Nội',
  phone: '024 3821 2709',
  website: null,
  photos: [],
  openingHours: [{ dayOfWeek: 1, openTime: '06:00:00', closeTime: '21:00:00' }],
  amenities: [{ id: 7, name: 'Wi-Fi miễn phí', slug: 'wifi', icon: null }],
  ratingDistribution: { '1': 0, '2': 0, '3': 1, '4': 2, '5': 7 },
  checkinCount: 3,
  owner: { id: 42, displayName: 'Minh Anh', avatarUrl: null },
  claimable: false,
  myReviewId: null,
  checkedInToday: false,
  rejectReason: null,
  version: 3,
}

/** 60 ngày: 30 ngày trước mỗi ngày 10 lượt xem, 30 ngày gần đây mỗi ngày 11 (▲ 10%). */
function stats(): PlaceStats {
  const series = (first: number, second: number) =>
    Array.from({ length: 60 }, (_, i) => ({
      date: `2026-08-${String((i % 28) + 1).padStart(2, '0')}`,
      count: i < 30 ? first : second,
    }))
  return {
    from: '2026-08-20',
    to: '2026-10-18',
    viewsByDay: series(10, 11),
    checkinsByDay: series(1, 1),
    ratingDistribution: { '3': 1, '4': 2, '5': 7 },
    bayesianScore: 4.412,
    reviewCount: 10,
  }
}

function review(id: number): Review {
  return {
    id,
    placeId: 5,
    place: { id: 5, slug: 'pho-thin-ha-noi', name: 'Phở Thìn' },
    author: { id: 100 + id, displayName: `Người ${id}`, avatarUrl: null },
    rating: 2,
    content: `Cuối tuần quá đông, không có chỗ gửi xe — số ${id}.`,
    visitedAt: '2026-10-01',
    status: 'PUBLISHED',
    rejectReason: null,
    photos: [],
    helpfulCount: 0,
    votedByMe: false,
    authorCheckedIn: false,
    commentCount: 0,
    ownerReply: null,
    createdAt: '2026-10-02T03:00:00Z',
    revisedAt: null,
    updatedAt: '2026-10-02T03:00:00Z',
    version: 0,
  }
}

let router: Router

async function mountPage(id: number, tab?: string): Promise<VueWrapper> {
  const Empty = { render: () => null }
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Empty },
      { path: '/owner', name: 'owner', component: Empty },
      { path: '/owner/places/:id', name: 'owner-place', component: Empty },
      { path: '/places/:slug', name: 'place', component: Empty },
    ],
  })
  await router.push({ path: `/owner/places/${id}`, query: tab ? { tab } : {} })
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(OwnerPlacePage, {
    props: { id },
    global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  setActivePinia(createPinia())
  Object.values({ ...owner, ...places }).forEach((fn) => fn.mockReset())
  owner.places.mockResolvedValue([PLACE])
  owner.stats.mockResolvedValue(stats())
  places.detail.mockResolvedValue(structuredClone(PLACE))
  places.reviews.mockResolvedValue({ items: [review(1), review(2)], nextCursor: null })
})

describe('OwnerPlacePage (P09 /owner/places/:id)', () => {
  it('địa điểm không thuộc mình → trang không tìm thấy, không tải chi tiết', async () => {
    const wrapper = await mountPage(99)
    expect(wrapper.text()).toContain('Không tìm thấy trang này')
    expect(places.detail).not.toHaveBeenCalled()
  })

  it('Thống kê: KPI theo kỳ + so với kỳ trước, điểm Bayesian, cột "Cần phản hồi"', async () => {
    const wrapper = await mountPage(5)
    expect(wrapper.get('h1').text()).toBe('Phở Thìn')
    expect(wrapper.text()).toContain('✓ Đã xác nhận sở hữu')
    // Một lần gọi cho 2 kỳ × 30 ngày
    const [placeId, from, to] = owner.stats.mock.calls[0]!
    expect(placeId).toBe(5)
    const span = (Date.parse(to) - Date.parse(from)) / 86_400_000 + 1
    expect(span).toBe(60)
    const kpis = wrapper.findAll('.ls-kpi').map((k) => k.text())
    expect(kpis[0]).toContain('330') // 30 × 11
    expect(kpis[0]).toContain('▲ 10%')
    expect(kpis[1]).toContain('Không đổi')
    expect(kpis[2]).toContain('4.4')
    expect(places.reviews).toHaveBeenCalledWith(
      5,
      expect.objectContaining({ unreplied: true, limit: 3 }),
    )
    expect(wrapper.find('svg.chart').exists()).toBe(true)
    expect(wrapper.find('table.sr-only').exists()).toBe(true)
  })

  it('Thông tin: điền sẵn, gửi PATCH đủ trường kèm version; giá cũ không xoá được', async () => {
    owner.update.mockResolvedValue({ ...PLACE, version: 4 })
    const wrapper = await mountPage(5, 'info')
    const description = wrapper.get('textarea').element as HTMLTextAreaElement
    expect(description.value).toBe('Phở bò tái lăn')

    await wrapper.get('input[type="tel"]').setValue('')
    const boxes = wrapper.findAll('input[type="checkbox"]')
    await boxes[boxes.length - 1]!.setValue(true) // Chỗ gửi xe
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(owner.update).toHaveBeenCalledWith(5, {
      description: 'Phở bò tái lăn',
      address: '13 Lò Đúc',
      priceMin: 50000,
      priceMax: 80000,
      phone: '',
      website: '',
      openingHours: [{ dayOfWeek: 1, openTime: '06:00', closeTime: '21:00' }],
      amenityIds: [7, 8],
      version: 3,
    })

    owner.update.mockClear()
    const priceMin = wrapper.findAll('input[type="number"]')[0]!
    await priceMin.setValue('')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(owner.update).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Không xoá được giá đã có')
  })

  it('Thông tin: người khác vừa sửa (409) → báo tải lại', async () => {
    const config = { headers: {} } as InternalAxiosRequestConfig
    owner.update.mockRejectedValue(
      new AxiosError('x', 'ERR', config, null, {
        data: { code: 'VERSION_CONFLICT', detail: 'x' },
        status: 409,
        statusText: '',
        headers: {},
        config,
      }),
    )
    const wrapper = await mountPage(5, 'info')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Thông tin vừa được cập nhật ở nơi khác')
  })

  it('Đánh giá: mặc định "Cần phản hồi", trả lời tại chỗ, chuyển sang tất cả', async () => {
    owner.reply.mockResolvedValue({
      id: 70,
      owner: { id: 42, displayName: 'Minh Anh', avatarUrl: null },
      content: 'Quán đã thuê thêm bãi xe, mời bạn ghé lại!',
      createdAt: '2026-10-18T03:00:00Z',
      updatedAt: '2026-10-18T03:00:00Z',
    })
    const wrapper = await mountPage(5, 'reviews')
    expect(places.reviews).toHaveBeenCalledWith(
      5,
      expect.objectContaining({ unreplied: true, sort: 'NEWEST' }),
    )
    const pressed = wrapper.findAll('button.ls-fchip').map((b) => b.attributes('aria-pressed'))
    expect(pressed).toEqual(['true', 'false'])

    // Gửi trống → báo lỗi, không gọi API
    const firstForm = wrapper.findAll('li form')[0]!
    await firstForm.trigger('submit')
    expect(owner.reply).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Nhập nội dung phản hồi.')

    await firstForm.get('textarea').setValue('  Quán đã thuê thêm bãi xe, mời bạn ghé lại!  ')
    await firstForm.trigger('submit')
    await flushPromises()
    expect(owner.reply).toHaveBeenCalledWith(1, 'Quán đã thuê thêm bãi xe, mời bạn ghé lại!')
    expect(wrapper.text()).toContain('Phản hồi của bạn')

    places.reviews.mockClear()
    await wrapper
      .findAll('button.ls-fchip')
      .find((b) => b.text() === 'Tất cả đánh giá')!
      .trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ tab: 'reviews', filter: 'all' })
    expect(places.reviews).toHaveBeenCalledWith(5, expect.objectContaining({ unreplied: false }))
  })
})
