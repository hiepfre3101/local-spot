import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { MeResponse, PlaceDetail, Review } from '@/types/api'
import { todayInVietnam } from '@/validation/review'

import WriteReviewPage from '../WriteReviewPage.vue'

const detail = vi.fn()
const getReview = vi.fn()
const create = vi.fn()
const update = vi.fn()
const remove = vi.fn()

vi.mock('@/api/places', () => ({
  placesApi: { detail: (...args: unknown[]) => detail(...args) },
}))
vi.mock('@/api/reviews', () => ({
  reviewsApi: {
    get: (...args: unknown[]) => getReview(...args),
    create: (...args: unknown[]) => create(...args),
    update: (...args: unknown[]) => update(...args),
    remove: (...args: unknown[]) => remove(...args),
    addPhotos: vi.fn(),
    deletePhoto: vi.fn(),
  },
}))

const PLACE: PlaceDetail = {
  id: 5,
  slug: 'pho-thin-ha-noi',
  name: 'Phở Thìn',
  category: { id: 11, name: 'Phở & bún', slug: 'pho-bun' },
  address: '13 Lò Đúc',
  location: { lat: 21.0175, lng: 105.8523 },
  coverPhoto: null,
  priceMin: null,
  priceMax: null,
  bayesianScore: 4.412,
  avgRating: 4.6,
  reviewCount: 10,
  status: 'APPROVED',
  distanceM: null,
  tags: [],
  description: null,
  city: 'Hà Nội',
  phone: null,
  website: null,
  photos: [],
  openingHours: [],
  amenities: [],
  ratingDistribution: { '1': 0, '2': 0, '3': 1, '4': 2, '5': 7 },
  checkinCount: 0,
  owner: null,
  claimable: true,
  myReviewId: null,
  checkedInToday: false,
  rejectReason: null,
  version: 0,
}

const ME: MeResponse = {
  id: 42,
  displayName: 'Minh Anh',
  avatarUrl: null,
  email: 'minh.anh@localspot.test',
  bio: null,
  emailVerified: true,
  roles: ['USER'],
  permissions: ['review:create', 'review:update-own', 'review:delete-own'],
  trustScore: 10,
  ownedPlaceIds: [],
}

const CONTENT = 'Nước dùng trong, thịt bò mềm, nên đến trước 7 giờ sáng.'

function myReview(overrides: Partial<Review> = {}): Review {
  return {
    id: 77,
    placeId: 5,
    place: { id: 5, slug: 'pho-thin-ha-noi', name: 'Phở Thìn' },
    author: { id: 42, displayName: 'Minh Anh', avatarUrl: null },
    rating: 4,
    content: CONTENT,
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
    updatedAt: '2026-10-02T03:00:00Z',
    version: 3,
    ...overrides,
  }
}

function httpError(status: number, data: object): AxiosError {
  const config = { headers: {} } as InternalAxiosRequestConfig
  return new AxiosError('x', 'ERR', config, null, {
    data,
    status,
    statusText: '',
    headers: {},
    config,
  })
}

let router: Router

async function mountPage(): Promise<VueWrapper> {
  const Empty = { render: () => null }
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Empty },
      { path: '/places/:slug', name: 'place', component: Empty },
      {
        path: '/places/:slug/review',
        name: 'write-review',
        component: WriteReviewPage,
        props: true,
      },
    ],
  })
  await router.push('/places/pho-thin-ha-noi/review')
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(WriteReviewPage, {
    props: { slug: 'pho-thin-ha-noi' },
    global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

async function fill(wrapper: VueWrapper, rating: number, content: string): Promise<void> {
  await wrapper.get(`.ls-rate input[value="${rating}"]`).setValue(true)
  await wrapper.get('textarea[name="content"]').setValue(content)
}

/** vee-validate kiểm schema bất đồng bộ (zod) qua vài vòng microtask / macrotask — chờ hết rồi mới kiểm kết quả. */
async function settle(): Promise<void> {
  for (let i = 0; i < 5; i++) {
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}

async function submit(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('form').trigger('submit')
  await settle()
}

describe('WriteReviewPage (P04)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useAuthStore().$patch({ user: { ...ME } })
    detail.mockReset().mockResolvedValue(PLACE)
    getReview.mockReset()
    create.mockReset()
    update.mockReset()
    remove.mockReset()
  })

  it('viết mới: gửi đúng dữ liệu, trust thấp → màn "đang chờ duyệt"', async () => {
    create.mockResolvedValue(myReview({ status: 'PENDING' }))
    const wrapper = await mountPage()
    expect(wrapper.get('h1').text()).toBe('Bạn thấy nơi này thế nào?')
    expect(wrapper.get('input[name="visitedAt"]').element).toHaveProperty('value', todayInVietnam())

    await fill(wrapper, 4, `  ${CONTENT}  `)
    expect(wrapper.text()).toContain('4 · Rất tốt')
    await submit(wrapper)

    expect(create).toHaveBeenCalledWith(
      5,
      { rating: 4, content: CONTENT, visitedAt: todayInVietnam() },
      [],
    )
    expect(wrapper.get('h1').text()).toBe('Đã gửi — đang chờ duyệt')
  })

  it('kiểm ngay trên form: chưa chọn điểm, nội dung quá ngắn → không gọi API', async () => {
    const wrapper = await mountPage()
    await wrapper.get('textarea[name="content"]').setValue('Ngon')
    await submit(wrapper)

    expect(create).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Chọn số điểm.')
    expect(wrapper.text()).toContain('Viết ít nhất 20 ký tự.')
    expect(wrapper.get('.ls-rate').attributes('aria-invalid')).toBe('true')
  })

  it('lỗi 422 của backend: gắn vào ô, lỗi ảnh nói rõ ảnh thứ mấy', async () => {
    create.mockRejectedValue(
      httpError(422, {
        code: 'VALIDATION_FAILED',
        detail: 'Dữ liệu không hợp lệ.',
        errors: [
          { field: 'visitedAt', message: 'Ngày đã đến không được ở tương lai.' },
          { field: 'photos[1]', message: 'Chỉ nhận ảnh JPEG hoặc PNG.' },
        ],
      }),
    )
    const wrapper = await mountPage()
    await fill(wrapper, 5, CONTENT)
    await submit(wrapper)

    expect(wrapper.text()).toContain('Ngày đã đến không được ở tương lai.')
    expect(wrapper.get('[role="alert"]').text()).toContain('Ảnh thứ 2: Chỉ nhận ảnh JPEG hoặc PNG.')
    expect(wrapper.find('h1').text()).toBe('Bạn thấy nơi này thế nào?')
  })

  it('đã có đánh giá → chế độ sửa: điền sẵn, gửi kèm version, đăng ngay → "Đã lưu thay đổi"', async () => {
    detail.mockResolvedValue({ ...PLACE, myReviewId: 77 })
    getReview.mockResolvedValue(myReview())
    update.mockResolvedValue(myReview({ rating: 5, version: 4 }))
    const wrapper = await mountPage()

    expect(getReview).toHaveBeenCalledWith(77)
    expect(wrapper.get('h1').text()).toBe('Sửa đánh giá')
    expect(wrapper.get('textarea[name="content"]').element).toHaveProperty('value', CONTENT)
    expect(wrapper.text()).toContain('4 · Rất tốt')

    await wrapper.get('.ls-rate input[value="5"]').setValue(true)
    await submit(wrapper)

    expect(update).toHaveBeenCalledWith(77, {
      rating: 5,
      content: CONTENT,
      visitedAt: '2026-10-01',
      version: 3,
    })
    expect(wrapper.get('h1').text()).toBe('Đã lưu thay đổi')
  })

  it('đánh giá bị từ chối: hiện lý do để sửa rồi gửi lại', async () => {
    detail.mockResolvedValue({ ...PLACE, myReviewId: 77 })
    getReview.mockResolvedValue(
      myReview({ status: 'REJECTED', rejectReason: 'Nội dung mang tính quảng cáo.' }),
    )
    const wrapper = await mountPage()
    expect(wrapper.text()).toContain('Đánh giá bị từ chối: Nội dung mang tính quảng cáo.')
    expect(wrapper.find('form').exists()).toBe(true)
  })

  it('đã xoá đánh giá trước đó (404) → giải thích không viết lại được, không có form', async () => {
    detail.mockResolvedValue({ ...PLACE, myReviewId: 77 })
    getReview.mockRejectedValue(httpError(404, { code: 'REVIEW_NOT_FOUND' }))
    const wrapper = await mountPage()
    expect(wrapper.text()).toContain('Bạn đã xoá đánh giá cho địa điểm này')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('chủ địa điểm không tự đánh giá; chưa xác thực email thì không gửi được', async () => {
    useAuthStore().$patch({ user: { ...ME, ownedPlaceIds: [5] } })
    let wrapper = await mountPage()
    expect(wrapper.text()).toContain('Bạn là chủ địa điểm này nên không thể tự đánh giá.')
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.unmount()

    useAuthStore().$patch({ user: { ...ME, emailVerified: false } })
    wrapper = await mountPage()
    expect(wrapper.text()).toContain('Xác thực email trước khi gửi đánh giá')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('xoá đánh giá cần xác nhận, xong quay về trang địa điểm', async () => {
    detail.mockResolvedValue({ ...PLACE, myReviewId: 77 })
    getReview.mockResolvedValue(myReview())
    remove.mockResolvedValue(undefined)
    const wrapper = await mountPage()

    const deleteButton = wrapper.findAll('button').find((b) => b.text() === 'Xoá đánh giá')!
    await deleteButton.trigger('click')
    expect(remove).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('không thể viết lại đánh giá')

    const confirm = wrapper.findAll('button').find((b) => b.text() === 'Xoá vĩnh viễn')!
    await confirm.trigger('click')
    await flushPromises()
    expect(remove).toHaveBeenCalledWith(77)
    expect(router.currentRoute.value.name).toBe('place')
  })
})
