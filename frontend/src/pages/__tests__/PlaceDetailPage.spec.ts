import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { CollectionSummary, MeResponse, PlaceDetail, Review } from '@/types/api'
import { PositionError } from '@/utils/position'

import PlaceDetailPage from '../PlaceDetailPage.vue'

const detail = vi.fn()
const reviews = vi.fn()
const list = vi.fn()
const nearby = vi.fn()

vi.mock('@/api/places', () => ({
  placesApi: {
    detail: (...args: unknown[]) => detail(...args),
    reviews: (...args: unknown[]) => reviews(...args),
    list: (...args: unknown[]) => list(...args),
    nearby: (...args: unknown[]) => nearby(...args),
  },
}))
const mine = vi.fn()
const addPlace = vi.fn()
const removePlace = vi.fn()
vi.mock('@/api/collections', () => ({
  collectionsApi: {
    mine: (...args: unknown[]) => mine(...args),
    addPlace: (...args: unknown[]) => addPlace(...args),
    removePlace: (...args: unknown[]) => removePlace(...args),
  },
}))
vi.mock('@/plugins/toast', () => ({
  useNotify: () => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }),
}))

const COLLECTION: CollectionSummary = {
  id: 1,
  name: 'Ăn sáng',
  isPublic: false,
  shareSlug: null,
  placeCount: 1,
  coverPhoto: null,
}

const ME: MeResponse = {
  id: 42,
  displayName: 'Minh Anh',
  avatarUrl: null,
  email: 'minh.anh@localspot.test',
  bio: null,
  emailVerified: true,
  roles: ['USER'],
  permissions: [],
  trustScore: 40,
  ownedPlaceIds: [],
}

const checkIn = vi.fn()
vi.mock('@/api/checkins', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/checkins')>()),
  checkInsApi: { checkIn: (...a: unknown[]) => checkIn(...a) },
}))
const measure = vi.fn()
vi.mock('@/utils/position', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/position')>()),
  measurePosition: (...a: unknown[]) => measure(...a),
}))

vi.mock('@/components/place/PlaceMap.vue', () => ({
  __esModule: true,
  default: { name: 'PlaceMap', render: () => null },
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
  tags: ['Gia truyền'],
  description: 'Phở bò tái lăn',
  city: 'Hà Nội',
  phone: '024 3821 2709',
  website: null,
  photos: [],
  openingHours: [],
  amenities: [{ id: 7, name: 'Wi-Fi miễn phí', slug: 'wifi', icon: null }],
  ratingDistribution: { '1': 0, '2': 0, '3': 1, '4': 2, '5': 7 },
  checkinCount: 3,
  owner: null,
  claimable: true,
  myReviewId: null,
  checkedInToday: false,
  rejectReason: null,
  version: 0,
}

function review(id: number, rating: number, helpfulCount: number): Review {
  return {
    id,
    placeId: 5,
    place: { id: 5, slug: 'pho-thin-ha-noi', name: 'Phở Thìn' },
    author: { id: 100 + id, displayName: `Người ${id}`, avatarUrl: null },
    rating,
    content: `Nội dung đánh giá số ${id} đủ dài.`,
    visitedAt: '2026-10-01',
    status: 'PUBLISHED',
    rejectReason: null,
    photos: [],
    helpfulCount,
    votedByMe: false,
    authorCheckedIn: id === 1,
    commentCount: 0,
    ownerReply: null,
    createdAt: '2026-10-02T03:00:00Z',
    revisedAt: null,
    updatedAt: '2026-10-02T03:00:00Z',
    version: 0,
  }
}

async function mountAt(slug: string, options: { writeReviewRoute?: boolean } = {}) {
  const Empty = { render: () => null }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Empty },
      { path: '/search', name: 'search', component: Empty },
      { path: '/login', name: 'login', component: Empty },
      { path: '/users/:id', name: 'profile', component: Empty },
      { path: '/owner', name: 'owner', component: Empty },
      { path: '/places/:slug', name: 'place', component: PlaceDetailPage, props: true },
      ...(options.writeReviewRoute
        ? [{ path: '/places/:slug/review', name: 'write-review', component: Empty }]
        : []),
    ],
  })
  await router.push(`/places/${slug}`)
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(PlaceDetailPage, {
    props: { slug },
    global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

describe('PlaceDetailPage (P03)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    detail.mockReset().mockResolvedValue(PLACE)
    reviews.mockReset().mockResolvedValue({
      items: [review(1, 5, 12), review(2, 3, 30), review(3, 4, 0)],
      nextCursor: null,
    })
    list.mockReset().mockResolvedValue({ items: [], nextCursor: null })
    nearby.mockReset().mockResolvedValue([])
  })

  it('hiện thông tin, điểm Bayesian kèm trung bình thô, phân bố sao', async () => {
    const wrapper = await mountAt('pho-thin-ha-noi')
    expect(document.title).toBe('Phở Thìn · LocalSpot')
    expect(wrapper.get('h1').text()).toBe('Phở Thìn')
    expect(wrapper.text()).toContain('4.4')
    expect(wrapper.text()).toContain('trung bình thô 4,6')
    expect(wrapper.text()).toContain('#gia-truyền')
    expect(wrapper.text()).toContain('50.000 – 80.000 ₫ / người')
    expect(wrapper.text()).toContain('Chưa có giờ mở cửa')
    expect(wrapper.get('a[href^="tel:"]').attributes('href')).toBe('tel:02438212709')
  })

  it('đánh giá nổi bật chỉ lấy từ 4 sao và có lượt hữu ích', async () => {
    const wrapper = await mountAt('pho-thin-ha-noi')
    const minis = wrapper.findAll('.ls-rmini')
    expect(minis).toHaveLength(1)
    expect(minis[0]!.text()).toContain('Người 1')
    expect(minis[0]!.text()).toContain('✓ Đã check-in')
  })

  it('bấm một mức sao ở phân bố → lọc danh sách theo số sao', async () => {
    const wrapper = await mountAt('pho-thin-ha-noi')
    reviews.mockClear()
    await wrapper.get('button[aria-label^="5 sao"]').trigger('click')
    await flushPromises()
    expect(reviews).toHaveBeenCalledWith(5, expect.objectContaining({ sort: 'HELPFUL', rating: 5 }))
    expect(wrapper.get('button[aria-label^="5 sao"]').attributes('aria-pressed')).toBe('true')
  })

  it('yêu cầu sở hữu: chỉ khi claimable, mở /owner với địa điểm chọn sẵn', async () => {
    let wrapper = await mountAt('pho-thin-ha-noi')
    const link = wrapper.findAll('a').find((a) => a.text() === 'Yêu cầu xác nhận sở hữu')!
    expect(link.attributes('href')).toBe('/owner?place=pho-thin-ha-noi')
    wrapper.unmount()

    detail.mockResolvedValue({ ...PLACE, claimable: false })
    wrapper = await mountAt('pho-thin-ha-noi')
    expect(wrapper.text()).not.toContain('Yêu cầu xác nhận sở hữu')
  })

  describe('Check-in (UC16, P12)', () => {
    const button = (wrapper: Awaited<ReturnType<typeof mountAt>>, text: string) =>
      wrapper.findAll('button').find((b) => b.text() === text)

    beforeEach(() => {
      HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
        this.open = true
      }
      HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
        this.open = false
      }
      measure.mockReset().mockResolvedValue({ lat: 21.0179, lng: 105.8523, accuracyM: 15 })
      checkIn.mockReset()
      mine.mockReset().mockResolvedValue([])
    })

    it('khách bấm → đăng nhập, không đo vị trí', async () => {
      const wrapper = await mountAt('pho-thin-ha-noi')
      await button(wrapper, 'Check-in')!.trigger('click')
      await flushPromises()
      expect(wrapper.vm.$router.currentRoute.value.name).toBe('login')
      expect(measure).not.toHaveBeenCalled()
    })

    it('đã check-in hôm nay → nút tắt; chủ địa điểm không có nút', async () => {
      detail.mockResolvedValue({ ...PLACE, checkedInToday: true })
      let wrapper = await mountAt('pho-thin-ha-noi')
      expect(button(wrapper, '✓ Đã check-in hôm nay')!.attributes('disabled')).toBeDefined()
      wrapper.unmount()

      detail.mockResolvedValue(PLACE)
      useAuthStore().$patch({ user: { ...ME, ownedPlaceIds: [5] }, accessToken: 'token' })
      wrapper = await mountAt('pho-thin-ha-noi')
      expect(button(wrapper, 'Check-in')).toBeUndefined()
    })

    it('thành công: gửi vị trí đo được, báo khoảng cách, nút đổi trạng thái', async () => {
      useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
      // Máy chủ sau khi check-in: chi tiết nạp lại có trạng thái mới
      checkIn.mockImplementation(async () => {
        detail.mockResolvedValue({ ...PLACE, checkedInToday: true, checkinCount: 4 })
        return checkInResult
      })
      const checkInResult = {
        id: 1,
        placeId: 5,
        place: PLACE,
        distanceM: 42,
        checkinDate: '2026-10-19',
        createdAt: '2026-10-19T12:24:00Z',
      }
      const wrapper = await mountAt('pho-thin-ha-noi')
      const reviewCallsBefore = reviews.mock.calls.length
      await button(wrapper, 'Check-in')!.trigger('click')
      await flushPromises()
      expect(checkIn).toHaveBeenCalledWith(5, { lat: 21.0179, lng: 105.8523, accuracyM: 15 })
      const dialog = wrapper.get('dialog.ls-dialog')
      expect(dialog.text()).toContain('Check-in thành công')
      expect(dialog.text()).toContain('Bạn đang cách Phở Thìn 42 m')
      expect(button(wrapper, '✓ Đã check-in hôm nay')).toBeDefined()
      // Danh sách đánh giá nạp lại (nhãn "đã check-in" của đánh giá viết hôm nay đổi ngay)
      expect(reviews.mock.calls.length).toBeGreaterThan(reviewCallsBefore)
    })

    it('đã có đánh giá → gợi ý "Cập nhật đánh giá" theo lần ghé này', async () => {
      useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
      detail.mockResolvedValue({ ...PLACE, myReviewId: 77 })
      checkIn.mockResolvedValue({
        id: 2,
        placeId: 5,
        place: PLACE,
        distanceM: 15,
        checkinDate: '2026-10-19',
        createdAt: '2026-10-19T12:24:00Z',
      })
      const wrapper = await mountAt('pho-thin-ha-noi', { writeReviewRoute: true })
      await button(wrapper, 'Check-in')!.trigger('click')
      await flushPromises()
      const dialog = wrapper.get('dialog.ls-dialog')
      expect(dialog.text()).toContain('Thấy khác lần trước?')
      const link = dialog.findAll('a').find((a) => a.text() === 'Cập nhật đánh giá')!
      expect(link.attributes('href')).toBe('/places/pho-thin-ha-noi/review')
    })

    it('quá xa → khoảng cách từ máy chủ + Chỉ đường / Thử lại', async () => {
      useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
      const config = { headers: {} } as InternalAxiosRequestConfig
      checkIn.mockRejectedValue(
        new AxiosError('x', 'ERR', config, null, {
          data: { code: 'CHECKIN_TOO_FAR', detail: 'x', distanceM: 1200 },
          status: 422,
          statusText: '',
          headers: {},
          config,
        }),
      )
      const wrapper = await mountAt('pho-thin-ha-noi')
      await button(wrapper, 'Check-in')!.trigger('click')
      await flushPromises()
      const dialog = wrapper.get('dialog.ls-dialog')
      expect(dialog.text()).toContain('Bạn chưa ở đủ gần')
      expect(dialog.text()).toContain('cách khoảng 1,2 km')
      expect(dialog.text()).toContain('Chỉ đường')

      await button(wrapper, 'Thử lại')!.trigger('click')
      await flushPromises()
      expect(measure).toHaveBeenCalledTimes(2)
    })

    it('GPS kém (> 100 m) → không gửi; bị chặn quyền → hướng dẫn bật', async () => {
      useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
      measure.mockResolvedValue({ lat: 21, lng: 105, accuracyM: 300 })
      const wrapper = await mountAt('pho-thin-ha-noi')
      await button(wrapper, 'Check-in')!.trigger('click')
      await flushPromises()
      expect(checkIn).not.toHaveBeenCalled()
      expect(wrapper.get('dialog.ls-dialog').text()).toContain('±300 m')

      measure.mockRejectedValue(new PositionError('denied'))
      await button(wrapper, 'Thử lại')!.trigger('click')
      await flushPromises()
      const dialog = wrapper.get('dialog.ls-dialog')
      expect(dialog.text()).toContain('Chưa có quyền vị trí')
      await button(wrapper, 'Cách bật')!.trigger('click')
      expect(dialog.text()).toContain('Cho phép')
    })
  })

  describe('Lưu vào bộ sưu tập (UC18)', () => {
    const saveButton = (wrapper: Awaited<ReturnType<typeof mountAt>>) =>
      wrapper.findAll('button').find((b) => /^(\+ Lưu|✓ Đã lưu)$/.test(b.text()))

    beforeEach(() => {
      HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
        this.open = true
      }
      HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
        this.open = false
      }
      mine.mockReset().mockResolvedValue([
        { ...COLLECTION, id: 1, name: 'Ăn sáng', containsPlace: false },
        { ...COLLECTION, id: 2, name: 'Phở ngon', containsPlace: true, placeCount: 3 },
      ])
      addPlace.mockReset().mockResolvedValue(undefined)
      removePlace.mockReset().mockResolvedValue(undefined)
    })

    it('khách bấm → đăng nhập, không gọi API bộ sưu tập', async () => {
      const wrapper = await mountAt('pho-thin-ha-noi')
      await saveButton(wrapper)!.trigger('click')
      await flushPromises()
      expect(wrapper.vm.$router.currentRoute.value.name).toBe('login')
      expect(mine).not.toHaveBeenCalled()
    })

    it('đã có trong một bộ → "✓ Đã lưu"; tick / bỏ tick gọi API ngay', async () => {
      useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
      const wrapper = await mountAt('pho-thin-ha-noi')
      expect(mine).toHaveBeenCalledWith(5)
      const button = saveButton(wrapper)!
      expect(button.text()).toBe('✓ Đã lưu')
      expect(button.attributes('aria-pressed')).toBe('true')

      await button.trigger('click')
      await flushPromises()
      const boxes = wrapper.findAll('dialog fieldset input[type="checkbox"]')
      expect(boxes.map((b) => (b.element as HTMLInputElement).checked)).toEqual([false, true])

      await boxes[0]!.trigger('change')
      await flushPromises()
      expect(addPlace).toHaveBeenCalledWith(1, 5)
      await boxes[1]!.trigger('change')
      await flushPromises()
      expect(removePlace).toHaveBeenCalledWith(2, 5)
    })

    it('chưa có trong bộ nào → "+ Lưu"; địa điểm chưa duyệt không có nút', async () => {
      useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
      mine.mockResolvedValue([{ ...COLLECTION, containsPlace: false }])
      let wrapper = await mountAt('pho-thin-ha-noi')
      expect(saveButton(wrapper)!.attributes('aria-pressed')).toBe('false')
      expect(saveButton(wrapper)!.text()).toBe('+ Lưu')
      wrapper.unmount()

      detail.mockResolvedValue({ ...PLACE, status: 'PENDING' })
      wrapper = await mountAt('pho-thin-ha-noi')
      expect(saveButton(wrapper)).toBeUndefined()
    })
  })

  it('nút đánh giá: viết khi chưa có, sửa khi đã có, chủ địa điểm không có', async () => {
    const reviewLink = (labels: string[]) =>
      labels.filter((l) => l === 'Viết đánh giá' || l === 'Sửa đánh giá của bạn')
    const labelsOf = async () =>
      (await mountAt('pho-thin-ha-noi', { writeReviewRoute: true }))
        .findAll('a')
        .map((el) => el.text())

    expect(reviewLink(await labelsOf())).toEqual(['Viết đánh giá'])

    detail.mockResolvedValue({ ...PLACE, myReviewId: 77 })
    expect(reviewLink(await labelsOf())).toEqual(['Sửa đánh giá của bạn'])

    detail.mockResolvedValue(PLACE)
    useAuthStore().$patch({ user: { id: 9, ownedPlaceIds: [5] } as unknown as MeResponse })
    expect(reviewLink(await labelsOf())).toEqual([])
  })

  it('404 → trang không tìm thấy', async () => {
    const config = { headers: {} } as InternalAxiosRequestConfig
    detail.mockRejectedValue(
      new AxiosError('x', 'ERR', config, null, {
        data: { code: 'PLACE_NOT_FOUND' },
        status: 404,
        statusText: '',
        headers: {},
        config,
      }),
    )
    const wrapper = await mountAt('khong-ton-tai')
    expect(wrapper.text()).toContain('Không tìm thấy trang này')
    expect(reviews).not.toHaveBeenCalled()
  })
})
