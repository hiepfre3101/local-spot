import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { Dashboard, MeResponse, PlaceSummary } from '@/types/api'

import AdminDashboardPage from '../AdminDashboardPage.vue'

const get = vi.fn()
vi.mock('@/api/dashboard', () => ({ dashboardApi: { get: (...a: unknown[]) => get(...a) } }))
const queue = vi.fn()
vi.mock('@/api/moderation', () => ({ moderationApi: { queue: (...a: unknown[]) => queue(...a) } }))

const ADMIN: MeResponse = {
  id: 1,
  displayName: 'Lan Phạm',
  avatarUrl: null,
  email: 'admin@localspot.test',
  bio: null,
  emailVerified: true,
  roles: ['ADMIN'],
  permissions: [
    'dashboard:view',
    'place:approve',
    'review:moderate',
    'report:handle',
    'claim:approve',
  ],
  trustScore: 100,
  ownedPlaceIds: [],
}

const days = (counts: number[]) =>
  counts.map((count, i) => ({ date: `2026-10-${String(i + 6).padStart(2, '0')}`, count }))

function dashboard(overrides: Partial<Dashboard> = {}): Dashboard {
  return {
    from: '2026-10-06',
    to: '2026-10-19',
    generatedAt: '2026-10-19T02:40:00Z',
    totals: { newUsers: 1047, reviews: 2519, reports: 14 },
    previousTotals: { newUsers: 1047, reviews: 2241, reports: 9 },
    newUsersByDay: days([70, 80]),
    reviewsByDay: days([150, 210]),
    reviewsByCity: [
      { city: 'Đà Lạt', reviewCount: 612 },
      { city: 'Hội An', reviewCount: 488 },
    ],
    hotPlaces: [
      {
        place: { id: 5, slug: 'cho-dem-da-lat', name: 'Chợ đêm Đà Lạt' } as PlaceSummary,
        newReviewCount: 31,
      },
    ],
    pendingCounts: {
      places: 24,
      reviews: 62,
      reports: 6,
      claims: 7,
      oldestSubmittedAt: new Date(Date.now() - 2 * 86_400_000 - 3_600_000).toISOString(),
    },
    ...overrides,
  }
}

async function mountPage(): Promise<VueWrapper> {
  const Empty = { render: () => null }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Empty },
      { path: '/places/:slug', name: 'place', component: Empty },
      { path: '/admin/dashboard', name: 'admin-dashboard', component: Empty },
      { path: '/admin/queue', name: 'admin-queue', component: Empty },
      { path: '/admin/moderation/places/:id', name: 'admin-moderation-place', component: Empty },
      { path: '/admin/moderation/reviews/:id', name: 'admin-moderation-review', component: Empty },
    ],
  })
  await router.push('/admin/dashboard')
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(AdminDashboardPage, {
    global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  setActivePinia(createPinia())
  useAuthStore().$patch({ user: { ...ADMIN }, accessToken: 'token' })
  get.mockReset().mockResolvedValue(dashboard())
  queue.mockReset().mockResolvedValue({
    items: [
      {
        position: 1,
        type: 'PLACE',
        id: 9,
        title: 'Quán bún bò Cô Ba',
        submittedBy: 'Hà Vy',
        submittedAt: '2026-10-18T02:00:00Z',
        flags: [],
      },
      {
        position: 2,
        type: 'REVIEW',
        id: 40,
        title: 'Phố cổ Hội An',
        submittedBy: 'Tuấn Lê',
        submittedAt: '2026-10-18T03:00:00Z',
        flags: ['LOW_TRUST'],
      },
    ],
    nextCursor: 'x',
  })
})

describe('AdminDashboardPage (UC33)', () => {
  it('mặc định 14 ngày đến hôm nay (giờ VN); đổi khoảng gọi lại API', async () => {
    const wrapper = await mountPage()
    const [from, to] = get.mock.calls[0]!
    expect((Date.parse(to) - Date.parse(from)) / 86_400_000 + 1).toBe(14)
    expect(wrapper.text()).toContain('14 ngày qua · cập nhật')

    await wrapper.get('select').setValue('30')
    await flushPromises()
    const [from30, to30] = get.mock.calls[get.mock.calls.length - 1]!
    expect((Date.parse(to30) - Date.parse(from30)) / 86_400_000 + 1).toBe(30)
  })

  it('dải KPI: so với kỳ trước bằng ▲▼ + chữ; báo cáo tăng tô màu xấu', async () => {
    const wrapper = await mountPage()
    const kpis = wrapper.findAll('.ls-kpi')
    expect(kpis).toHaveLength(4)
    expect(kpis[0]!.text()).toContain('2.519')
    expect(kpis[0]!.text()).toContain('▲ 12,4% so với kỳ trước')
    expect(kpis[0]!.get('.ls-kpi__delta').classes()).toContain('ls-up')
    // Hàng chờ: địa điểm + đánh giá + sở hữu, kèm tuổi việc cũ nhất
    expect(kpis[1]!.text()).toContain('93')
    expect(kpis[1]!.text()).toContain('24 địa điểm · 62 đánh giá · 7 sở hữu · cũ nhất 2 ngày 1 giờ')
    expect(kpis[2]!.text()).toContain('▲ 55,6% · 6 chưa xử lý')
    expect(kpis[2]!.get('.ls-kpi__delta').classes()).toContain('ls-down')
    expect(kpis[3]!.text()).toContain('Không đổi so với kỳ trước')
  })

  it('biểu đồ + xếp hạng khu vực / địa điểm; bảng số liệu ẩn cho trình đọc màn hình', async () => {
    const wrapper = await mountPage()
    expect(wrapper.findAll('svg.chart')).toHaveLength(2)
    expect(wrapper.findAll('table.sr-only caption').map((c) => c.text())).toEqual([
      'Đánh giá được gửi mỗi ngày trong 14 ngày qua',
      'Tài khoản mới mỗi ngày trong 14 ngày qua',
    ])
    const ranks = wrapper.findAll('ol.rank li').map((li) => li.text())
    expect(ranks).toEqual(['Đà Lạt612', 'Hội An488', 'Chợ đêm Đà Lạt31'])
    const hot = wrapper.findAll('ol.rank a')[0]!
    expect(hot.attributes('href')).toBe('/places/cho-dem-da-lat')
  })

  it('hàng chờ: 5 mục đầu FIFO, hàng đầu "Xử lý tiếp"', async () => {
    const wrapper = await mountPage()
    expect(queue).toHaveBeenCalledWith(null, null, 5)
    const rows = wrapper.findAll('table.ls-table tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0]!.text()).toContain('Xử lý tiếp')
    expect(rows[1]!.text()).toContain('▼ Điểm tin cậy thấp')
  })

  it('không có quyền kiểm duyệt → không tải hàng chờ', async () => {
    useAuthStore().$patch({ user: { ...ADMIN, permissions: ['dashboard:view'] } })
    const wrapper = await mountPage()
    expect(queue).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('Hàng chờ kiểm duyệt ·')
  })

  it('lỗi → thông báo + thử lại', async () => {
    const config = { headers: {} } as InternalAxiosRequestConfig
    get.mockRejectedValue(
      new AxiosError('x', 'ERR', config, null, {
        data: { code: 'INTERNAL_ERROR', detail: 'Đã có lỗi xảy ra.' },
        status: 500,
        statusText: '',
        headers: {},
        config,
      }),
    )
    const wrapper = await mountPage()
    expect(wrapper.get('[role="alert"]').text()).toContain('Thử lại')
  })
})
