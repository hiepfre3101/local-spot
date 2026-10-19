import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { type Component } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type {
  MeResponse,
  ModerationClaim,
  ModerationPlaceDetail,
  ModerationQueueItem,
  ModerationReviewDetail,
  PlaceDetail,
  ReportGroupDetail,
  Review,
} from '@/types/api'

import ModerationClaimPage from '../ModerationClaimPage.vue'
import ModerationPlacePage from '../ModerationPlacePage.vue'
import ModerationQueuePage from '../ModerationQueuePage.vue'
import ModerationReportPage from '../ModerationReportPage.vue'
import ModerationReviewPage from '../ModerationReviewPage.vue'

const { api, notify } = vi.hoisted(() => ({
  api: {
    queue: vi.fn(),
    place: vi.fn(),
    decidePlace: vi.fn(),
    review: vi.fn(),
    decideReview: vi.fn(),
    reportGroup: vi.fn(),
    decideReports: vi.fn(),
    claim: vi.fn(),
    evidence: vi.fn(),
    decideClaim: vi.fn(),
  },
  notify: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
}))
vi.mock('@/api/moderation', () => ({ moderationApi: api }))
vi.mock('@/plugins/toast', () => ({ useNotify: () => notify }))
// Leaflet không chạy trong jsdom
vi.mock('@/components/place/PlaceMap.vue', () => ({ default: { render: () => null } }))

const ME = {
  id: 2,
  displayName: 'Kiểm duyệt 1',
  ownedPlaceIds: [] as number[],
} as unknown as MeResponse
const MOD_PERMISSIONS = ['place:approve', 'review:moderate', 'report:handle', 'claim:approve']
const blank = { render: () => null }

function conflict(code: string): AxiosError {
  const config = { headers: {} } as InternalAxiosRequestConfig
  return new AxiosError('409', 'ERR', config, null, {
    status: 409,
    statusText: 'Conflict',
    headers: {},
    config,
    data: { code, detail: 'Mục này đã được xử lý.' },
  })
}

async function mountPage(page: Component, path: string, permissions = MOD_PERMISSIONS) {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  auth.accessToken = 'token'
  auth.user = { ...ME, permissions } as MeResponse
  const router: Router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: blank },
      { path: '/places/:slug', name: 'place', component: blank },
      { path: '/users/:id', name: 'profile', component: blank },
      { path: '/admin/queue', name: 'admin-queue', component: ModerationQueuePage },
      {
        path: '/admin/moderation/places/:id',
        name: 'admin-moderation-place',
        component: ModerationPlacePage,
      },
      {
        path: '/admin/moderation/reviews/:id',
        name: 'admin-moderation-review',
        component: ModerationReviewPage,
      },
      {
        path: '/admin/moderation/reports/:targetType/:targetId',
        name: 'admin-moderation-report',
        component: ModerationReportPage,
      },
      {
        path: '/admin/moderation/claims/:id',
        name: 'admin-moderation-claim',
        component: ModerationClaimPage,
      },
    ],
  })
  await router.push(path)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(page, { global: { plugins: [router, [VueQueryPlugin, { queryClient }]] } })
  await flushPromises()
  return { wrapper, router }
}

function button(wrapper: Awaited<ReturnType<typeof mountPage>>['wrapper'], text: string) {
  const found = wrapper.findAll('button').find((b) => b.text().trim() === text)
  if (!found) throw new Error(`Không thấy nút "${text}"`)
  return found
}

const placeDetail = (overrides: Partial<PlaceDetail> = {}): ModerationPlaceDetail => ({
  place: {
    id: 12,
    slug: 'bun-bo-co-ba',
    name: 'Quán bún bò Cô Ba',
    category: { id: 1, name: 'Ăn uống', slug: 'an-uong' },
    address: '12 Lê Lợi',
    location: { lat: 11.94, lng: 108.44 },
    coverPhoto: null,
    priceMin: null,
    priceMax: null,
    bayesianScore: 0,
    avgRating: 0,
    reviewCount: 0,
    status: 'PENDING',
    distanceM: null,
    tags: [],
    description: 'Bún bò gia truyền',
    city: 'Đà Lạt',
    phone: null,
    website: null,
    photos: [],
    openingHours: [],
    amenities: [],
    ratingDistribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 },
    checkinCount: 0,
    owner: null,
    claimable: false,
    myReviewId: null,
    checkedInToday: false,
    rejectReason: null,
    version: 3,
    ...overrides,
  } as PlaceDetail,
  proposer: { id: 40, displayName: 'Hà Vy', avatarUrl: null },
  submittedAt: '2026-10-19T02:12:00Z',
  possibleDuplicates: [],
})

beforeEach(() => {
  vi.clearAllMocks()
  api.queue.mockResolvedValue({ items: [], nextCursor: null })
})

describe('Hàng chờ kiểm duyệt', () => {
  const items: ModerationQueueItem[] = [
    {
      position: 1,
      type: 'REPORT',
      id: 44,
      reportTargetType: 'COMMENT',
      title: 'Bình luận quảng cáo',
      submittedBy: 'Quang Huy',
      submittedAt: '2026-10-17T01:10:00Z',
      flags: [],
      reportCount: 3,
    },
    {
      position: 2,
      type: 'REVIEW',
      id: 9,
      title: 'Phố cổ — Quá đông',
      submittedBy: null,
      submittedAt: '2026-10-19T00:05:00Z',
      flags: ['IP_FLAGGED', 'LOW_TRUST'],
    },
  ]

  it('FIFO: cột #, chỉ hàng đầu là "Xử lý tiếp", dòng phụ nói vì sao mục ở đây', async () => {
    api.queue.mockResolvedValue({ items, nextCursor: null })
    const { wrapper } = await mountPage(ModerationQueuePage, '/admin/queue')

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0]!.text()).toContain('Xử lý tiếp')
    expect(rows[0]!.text()).toContain('Bình luận · 3 báo cáo')
    expect(rows[0]!.get('a').attributes('href')).toBe('/admin/moderation/reports/COMMENT/44')
    expect(rows[1]!.text()).toContain('Xem')
    expect(rows[1]!.text()).not.toContain('Xử lý tiếp')
    expect(rows[1]!.text()).toContain('⚑ Cùng dải IP · ▼ Điểm tin cậy thấp')
    expect(rows[1]!.text()).toContain('Tài khoản đã xoá')
    expect(api.queue).toHaveBeenCalledWith(null, null)
  })

  it('lọc loại chỉ trong các loại có quyền; ?type không có quyền thì bỏ qua (không gọi 403)', async () => {
    const { wrapper } = await mountPage(ModerationQueuePage, '/admin/queue?type=CLAIM', [
      'place:approve',
      'review:moderate',
    ])

    expect(api.queue).toHaveBeenCalledWith(null, null)
    const options = wrapper.findAll('option').map((o) => o.text())
    expect(options).toEqual(['Mọi loại việc', 'Địa điểm', 'Đánh giá'])
  })
})

describe('Duyệt địa điểm', () => {
  it('từ chối thiếu lý do → báo lỗi, không gọi API', async () => {
    api.place.mockResolvedValue(placeDetail())
    const { wrapper } = await mountPage(ModerationPlacePage, '/admin/moderation/places/12')

    await button(wrapper, 'Từ chối').trigger('click')

    expect(wrapper.text()).toContain('✕ Chọn lý do')
    expect(api.decidePlace).not.toHaveBeenCalled()
  })

  it('từ chối gửi lý do ghép + version rồi sang việc kế tiếp cùng loại', async () => {
    api.place.mockResolvedValue(placeDetail())
    api.decidePlace.mockResolvedValue(undefined)
    api.queue.mockResolvedValue({
      items: [
        {
          position: 1,
          type: 'PLACE',
          id: 13,
          title: 'Khác',
          submittedBy: 'A',
          submittedAt: '',
          flags: [],
        },
      ],
      nextCursor: null,
    })
    const { wrapper, router } = await mountPage(ModerationPlacePage, '/admin/moderation/places/12')

    await wrapper.get('select').setValue('Trùng địa điểm đã có')
    await wrapper.get('textarea').setValue('đã có ở #8')
    await button(wrapper, 'Từ chối').trigger('click')
    await flushPromises()

    expect(api.decidePlace).toHaveBeenCalledWith(12, {
      decision: 'REJECT',
      reason: 'Trùng địa điểm đã có — đã có ở #8',
      version: 3,
    })
    expect(api.queue).toHaveBeenCalledWith('PLACE', null, 1)
    expect(router.currentRoute.value.fullPath).toBe('/admin/moderation/places/13')
  })

  it('duyệt: hết việc → về hàng chờ của loại đó', async () => {
    api.place.mockResolvedValue(placeDetail())
    api.decidePlace.mockResolvedValue(undefined)
    const { wrapper, router } = await mountPage(ModerationPlacePage, '/admin/moderation/places/12')

    await button(wrapper, 'Duyệt & đăng').trigger('click')
    await flushPromises()

    expect(api.decidePlace).toHaveBeenCalledWith(12, { decision: 'APPROVE', version: 3 })
    expect(router.currentRoute.value.fullPath).toBe('/admin/queue?type=PLACE')
    expect(notify.info).toHaveBeenCalled()
  })

  it('người khác vừa xử lý (409) → báo lỗi và tải lại để thấy kết quả', async () => {
    api.place.mockResolvedValue(placeDetail())
    api.decidePlace.mockRejectedValue(conflict('PLACE_ALREADY_MODERATED'))
    const { wrapper } = await mountPage(ModerationPlacePage, '/admin/moderation/places/12')

    api.place.mockResolvedValue(placeDetail({ status: 'APPROVED' }))
    await button(wrapper, 'Duyệt & đăng').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('✕ Mục này đã được xử lý.')
    expect(api.place).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('✓ Đã duyệt')
    expect(wrapper.findAll('button').some((b) => b.text() === 'Duyệt & đăng')).toBe(false)
  })

  it('đề xuất của chính mình: nút bị khoá, có lời giải thích', async () => {
    const own = placeDetail()
    own.proposer = { id: ME.id, displayName: ME.displayName, avatarUrl: null }
    api.place.mockResolvedValue(own)
    const { wrapper } = await mountPage(ModerationPlacePage, '/admin/moderation/places/12')

    expect(button(wrapper, 'Duyệt & đăng').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('đề xuất của chính bạn')
  })
})

describe('Duyệt đánh giá', () => {
  it('review về địa điểm mình sở hữu → không tự duyệt được', async () => {
    const review = {
      id: 9,
      author: { id: 40, displayName: 'Tuấn Lê', avatarUrl: null },
      rating: 2,
      content: 'Quá đông',
      visitedAt: '2026-10-10',
      status: 'PENDING',
      rejectReason: null,
      photos: [],
      createdAt: '2026-10-19T00:05:00Z',
      version: 0,
    } as unknown as Review
    const detail: ModerationReviewDetail = {
      item: {
        review,
        place: placeDetail().place,
        authorTrustScore: 12,
        authorActiveViolations: 1,
        ipFlagged: true,
        sameSubnetReviewCount24h: 2,
      },
      authorJoinedAt: '2026-09-01T00:00:00Z',
      authorReviewCounts: { PUBLISHED: 3, PENDING: 1, REJECTED: 0, HIDDEN: 0 },
      sameSubnetReviews: [],
    }
    api.review.mockResolvedValue(detail)
    setActivePinia(createPinia())
    ME.ownedPlaceIds = [12]
    try {
      const { wrapper } = await mountPage(ModerationReviewPage, '/admin/moderation/reviews/9')
      expect(button(wrapper, 'Duyệt & đăng').attributes('disabled')).toBeDefined()
      expect(wrapper.text()).toContain('⚑ Bị gắn cờ IP')
      expect(wrapper.text()).toContain('Đã đăng 3 · Chờ duyệt 1 · Bị từ chối 0 · Bị ẩn 0')
    } finally {
      ME.ownedPlaceIds = []
    }
  })
})

describe('Xử lý báo cáo', () => {
  const group = (targetType: 'PLACE' | 'REVIEW'): ReportGroupDetail => ({
    group: {
      targetType,
      targetId: 44,
      targetPreview: 'Quán bún bò Cô Ba',
      reportCount: 3,
      reasons: { SPAM: 2, WRONG_INFO: 1 },
      firstReportedAt: '2026-10-17T01:10:00Z',
      targetStatus: 'APPROVED',
    },
    author: { id: 40, displayName: 'Hà Vy', avatarUrl: null },
    reports: [
      {
        id: 1,
        reporter: { id: 50, displayName: 'Quang Huy', avatarUrl: null },
        reason: 'SPAM',
        detail: null,
        createdAt: '2026-10-17T01:10:00Z',
      },
    ],
  })

  it('ẩn địa điểm bắt buộc lý do xác nhận, chỉ trong các lý do đã bị báo cáo', async () => {
    api.reportGroup.mockResolvedValue(group('PLACE'))
    api.decideReports.mockResolvedValue(undefined)
    const { wrapper } = await mountPage(ModerationReportPage, '/admin/moderation/reports/PLACE/44')

    expect(wrapper.findAll('input[type="radio"]')).toHaveLength(2)
    await button(wrapper, 'Ẩn nội dung').trigger('click')
    expect(api.decideReports).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('✕ Chọn lý do xác nhận')

    await wrapper.get('input[value="SPAM"]').setValue(true)
    await button(wrapper, 'Ẩn nội dung').trigger('click')
    await flushPromises()
    expect(api.decideReports).toHaveBeenCalledWith({
      targetType: 'PLACE',
      targetId: 44,
      decision: 'HIDE',
      confirmedReason: 'SPAM',
      note: undefined,
    })
  })

  it('bỏ qua báo cáo đánh giá không cần lý do', async () => {
    api.reportGroup.mockResolvedValue(group('REVIEW'))
    api.decideReports.mockResolvedValue(undefined)
    const { wrapper } = await mountPage(ModerationReportPage, '/admin/moderation/reports/REVIEW/44')

    expect(wrapper.text()).toContain('ghi một vi phạm cho Hà Vy')
    await button(wrapper, 'Bỏ qua báo cáo').trigger('click')
    await flushPromises()
    expect(api.decideReports).toHaveBeenCalledWith(
      expect.objectContaining({ decision: 'DISMISS', confirmedReason: undefined }),
    )
  })

  it('không còn báo cáo đang mở (404) → nói rõ, có đường về hàng chờ', async () => {
    const config = { headers: {} } as InternalAxiosRequestConfig
    api.reportGroup.mockRejectedValue(
      new AxiosError('404', 'ERR', config, null, {
        status: 404,
        statusText: 'Not Found',
        headers: {},
        config,
        data: { code: 'REPORT_NOT_FOUND', detail: 'x' },
      }),
    )
    const { wrapper } = await mountPage(ModerationReportPage, '/admin/moderation/reports/REVIEW/44')

    expect(wrapper.text()).toContain('Không còn báo cáo đang mở')
    expect(wrapper.get('a').attributes('href')).toBe('/admin/queue?type=REPORT')
  })
})

describe('Duyệt yêu cầu sở hữu', () => {
  const claim: ModerationClaim = {
    id: 3,
    place: placeDetail().place,
    claimant: { id: 40, displayName: 'Nguyễn Giảng', avatarUrl: null },
    contactPhone: '0901234567',
    note: null,
    evidenceCount: 2,
    status: 'PENDING',
    rejectReason: null,
    otherPendingClaimCount: 1,
    createdAt: '2026-10-18T07:32:00Z',
    version: 0,
  }

  it('cảnh báo yêu cầu khác sẽ tự bị từ chối; minh chứng lấy link tạm từng file', async () => {
    api.claim.mockResolvedValue(claim)
    api.evidence.mockResolvedValue({
      url: 'https://minio.test/e.jpg',
      expiresAt: '2026-10-19T03:00:00Z',
    })
    api.decideClaim.mockResolvedValue(undefined)
    const { wrapper } = await mountPage(ModerationClaimPage, '/admin/moderation/claims/3')

    expect(wrapper.text()).toContain('Còn 1 yêu cầu khác đang chờ')
    expect(api.evidence).toHaveBeenCalledWith(3, 0)
    expect(api.evidence).toHaveBeenCalledWith(3, 1)
    expect(wrapper.findAll('img')).toHaveLength(2)

    await button(wrapper, 'Xác nhận chủ sở hữu').trigger('click')
    await flushPromises()
    expect(api.decideClaim).toHaveBeenCalledWith(3, { decision: 'APPROVE', version: 0 })
  })

  it('ảnh minh chứng lỗi (link hết hạn) → ký link mới một lần', async () => {
    api.claim.mockResolvedValue({ ...claim, evidenceCount: 1 })
    api.evidence.mockResolvedValue({ url: 'https://minio.test/e.jpg', expiresAt: '' })
    const { wrapper } = await mountPage(ModerationClaimPage, '/admin/moderation/claims/3')

    await wrapper.get('img').trigger('error')
    await flushPromises()
    await wrapper.get('img').trigger('error')
    await flushPromises()

    expect(api.evidence).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('✕ Không mở được file')
  })
})
