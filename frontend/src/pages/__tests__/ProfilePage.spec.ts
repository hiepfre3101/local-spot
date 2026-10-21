import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { MeResponse, PlaceSummary, PublicProfile, Review } from '@/types/api'

import ProfilePage from '../ProfilePage.vue'

const api = {
  profile: vi.fn(),
  reviews: vi.fn(),
  collections: vi.fn(),
  followers: vi.fn(),
  following: vi.fn(),
  follow: vi.fn(),
  unfollow: vi.fn(),
  myReviews: vi.fn(),
  myCollections: vi.fn(),
  myCheckIns: vi.fn(),
  myPlaces: vi.fn(),
}
vi.mock('@/api/users', () => ({
  usersApi: new Proxy(
    {},
    {
      get:
        (_, name: string) =>
        (...args: unknown[]) =>
          api[name as keyof typeof api](...args),
    },
  ),
}))
vi.mock('@/plugins/toast', () => ({
  useNotify: () => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }),
}))

const PROFILE: PublicProfile = {
  id: 100,
  displayName: 'Tuấn Lê',
  avatarUrl: null,
  bio: 'Săn mây, ăn đêm, cà phê sáng.',
  joinedAt: '2025-03-10T03:00:00Z',
  reviewCount: 12,
  followerCount: 7,
  followingCount: 3,
  contributionPoints: 145,
  badges: [
    {
      code: 'REVIEWER_2',
      name: 'Người đánh giá cấp 2',
      description: 'Có 10 đánh giá đang hiển thị.',
      icon: 'reviewer',
      awardedAt: '2026-09-01T03:00:00Z',
    },
  ],
  followedByMe: false,
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

function review(id: number, overrides: Partial<Review> = {}): Review {
  return {
    id,
    placeId: 5,
    place: { id: 5, slug: 'pho-thin-ha-noi', name: 'Phở Thìn' },
    author: { id: 100, displayName: 'Tuấn Lê', avatarUrl: null },
    rating: 4,
    content: `Đánh giá số ${id}.`,
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
    ...overrides,
  }
}

function place(id: number, status: PlaceSummary['status']): PlaceSummary {
  return {
    id,
    slug: `quan-${id}`,
    name: `Quán số ${id}`,
    category: { id: 11, name: 'Phở & bún', slug: 'pho-bun' },
    address: `${id} Hàng Gà`,
    city: 'Hà Nội',
    location: { lat: 21, lng: 105 },
    coverPhoto: null,
    priceMin: null,
    priceMax: null,
    bayesianScore: 0,
    avgRating: 0,
    reviewCount: 0,
    status,
    distanceM: null,
    tags: [],
  }
}

let router: Router

async function mountProfile(userId: number, tab?: string): Promise<VueWrapper> {
  const Empty = { render: () => null }
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Empty },
      { path: '/login', name: 'login', component: Empty },
      { path: '/places/:slug', name: 'place', component: Empty },
      { path: '/propose', name: 'propose', component: Empty },
      { path: '/settings/password', name: 'settings-password', component: Empty },
      { path: '/users/:id', name: 'profile', component: Empty },
      { path: '/collections/:id', name: 'collection', component: Empty },
      { path: '/c/:shareSlug', name: 'shared-collection', component: Empty },
    ],
  })
  await router.push({ path: `/users/${userId}`, query: tab ? { tab } : {} })
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(ProfilePage, {
    props: { id: userId },
    global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

const tabLabels = (wrapper: VueWrapper) =>
  wrapper.findAll('.ls-tabs a').map((a) => a.text().replace(/\d+$/, '').trim())

beforeEach(() => {
  setActivePinia(createPinia())
  Object.values(api).forEach((fn) => fn.mockReset())
  api.profile.mockResolvedValue({ ...PROFILE })
  api.reviews.mockResolvedValue({ items: [review(1)], nextCursor: null })
  api.myReviews.mockResolvedValue({ items: [], nextCursor: null })
})

describe('ProfilePage (P06)', () => {
  it('trang người khác: số liệu, huy hiệu, 3 tab, đánh giá kèm tên địa điểm (liên kết)', async () => {
    const wrapper = await mountProfile(100)
    expect(wrapper.get('h1').text()).toBe('Tuấn Lê')
    expect(wrapper.text()).toContain('Thành viên từ 03/2025')
    expect(wrapper.get('.ls-stats').text()).toContain('145Điểm đóng góp')
    expect(wrapper.text()).toContain('Người đánh giá cấp 2')
    expect(tabLabels(wrapper)).toEqual(['Đánh giá', 'Bộ sưu tập', 'Huy hiệu'])
    expect(api.reviews).toHaveBeenCalledWith(100, null)
    const placeLink = wrapper.get('article article a')
    expect(placeLink.text()).toBe('Phở Thìn')
    expect(placeLink.attributes('href')).toBe('/places/pho-thin-ha-noi')
  })

  it('theo dõi / bỏ theo dõi cập nhật nút và số người theo dõi; khách → đăng nhập', async () => {
    // Sau khi theo dõi trang nạp lại hồ sơ — máy chủ trả trạng thái mới
    api.follow.mockImplementation(async () => {
      api.profile.mockResolvedValue({ ...PROFILE, followedByMe: true, followerCount: 8 })
    })
    api.unfollow.mockResolvedValue(undefined)
    let wrapper = await mountProfile(100)
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '+ Theo dõi')!
      .trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('login')
    expect(api.follow).not.toHaveBeenCalled()
    wrapper.unmount()

    useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
    wrapper = await mountProfile(100)
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '+ Theo dõi')!
      .trigger('click')
    await flushPromises()
    expect(api.follow).toHaveBeenCalledWith(100)
    const pressed = wrapper.findAll('button').find((b) => b.text() === '✓ Đang theo dõi')!
    expect(pressed.attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('.ls-stats').text()).toContain('8Người theo dõi')
  })

  it('trang của mình: Cài đặt tài khoản, thêm tab Check-in và Đề xuất, đánh giá mọi trạng thái', async () => {
    useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
    api.profile.mockResolvedValue({ ...PROFILE, id: 42, displayName: 'Minh Anh' })
    api.myReviews.mockResolvedValue({
      items: [review(2, { status: 'REJECTED', rejectReason: 'Nội dung quảng cáo.' })],
      nextCursor: null,
    })
    const wrapper = await mountProfile(42)
    expect(wrapper.text()).toContain('Cài đặt tài khoản')
    expect(wrapper.text()).not.toContain('Theo dõi')
    expect(tabLabels(wrapper)).toEqual([
      'Đánh giá',
      'Bộ sưu tập',
      'Huy hiệu',
      'Check-in',
      'Đề xuất của tôi',
    ])
    expect(api.myReviews).toHaveBeenCalled()
    expect(api.reviews).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('✕ Bị từ chối: Nội dung quảng cáo.')
  })

  it('tab Đề xuất của tôi: trạng thái duyệt bằng ký hiệu + chữ', async () => {
    useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
    api.profile.mockResolvedValue({ ...PROFILE, id: 42 })
    api.myPlaces.mockResolvedValue({
      items: [place(1, 'PENDING'), place(2, 'APPROVED'), place(3, 'REJECTED')],
      nextCursor: null,
    })
    const wrapper = await mountProfile(42, 'places')
    expect(api.myPlaces).toHaveBeenCalled()
    expect(api.myReviews).not.toHaveBeenCalled() // chỉ tải tab đang mở
    const text = wrapper.text()
    expect(text).toContain('● Chờ duyệt')
    expect(text).toContain('✓ Đã duyệt')
    expect(text).toContain('✕ Bị từ chối')
  })

  it('tab của chính chủ không mở được trên trang người khác → về tab Đánh giá', async () => {
    const wrapper = await mountProfile(100, 'checkins')
    expect(api.myCheckIns).not.toHaveBeenCalled()
    expect(wrapper.get('.ls-tabs a[aria-current="page"]').text()).toContain('Đánh giá')
  })

  it('bộ sưu tập và huy hiệu', async () => {
    api.collections.mockResolvedValue([
      {
        id: 1,
        name: 'Ăn sáng Hà Nội',
        isPublic: true,
        shareSlug: 'an-sang-x1',
        placeCount: 12,
        coverPhoto: null,
      },
    ])
    let wrapper = await mountProfile(100, 'collections')
    expect(api.collections).toHaveBeenCalledWith(100)
    expect(wrapper.text()).toContain('12 địa điểm')
    // Bộ của người khác mở bằng link chia sẻ; không có ô tạo bộ
    expect(wrapper.get('.coll a').text()).toBe('Ăn sáng Hà Nội')
    expect(wrapper.get('.coll a').attributes('href')).toBe('/c/an-sang-x1')
    expect(wrapper.text()).not.toContain('+ Tạo bộ sưu tập')
    wrapper.unmount()

    wrapper = await mountProfile(100, 'badges')
    expect(wrapper.text()).toContain('Có 10 đánh giá đang hiển thị.')
    expect(wrapper.text()).toContain('Đạt 01/09/2026')
  })

  it('bộ sưu tập của mình: thẻ mở trang quản lý, có ô "+ Tạo bộ sưu tập"', async () => {
    useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
    api.profile.mockResolvedValue({ ...PROFILE, id: 42 })
    api.myCollections.mockResolvedValue([
      {
        id: 7,
        name: 'Riêng của tôi',
        isPublic: false,
        shareSlug: null,
        placeCount: 2,
        coverPhoto: null,
      },
    ])
    const wrapper = await mountProfile(42, 'collections')
    expect(api.myCollections).toHaveBeenCalled()
    expect(wrapper.get('.coll a').attributes('href')).toBe('/collections/7')
    expect(wrapper.text()).toContain('2 địa điểm · Riêng tư')
    expect(wrapper.text()).toContain('+ Tạo bộ sưu tập')
  })

  it('người dùng không tồn tại → trang không tìm thấy', async () => {
    const config = { headers: {} } as InternalAxiosRequestConfig
    api.profile.mockRejectedValue(
      new AxiosError('x', 'ERR', config, null, {
        data: { code: 'USER_NOT_FOUND' },
        status: 404,
        statusText: '',
        headers: {},
        config,
      }),
    )
    const wrapper = await mountProfile(999)
    expect(wrapper.text()).toContain('Không tìm thấy trang này')
  })
})
