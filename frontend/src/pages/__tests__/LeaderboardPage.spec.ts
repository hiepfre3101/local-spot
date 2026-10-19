import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { LeaderboardEntry, MeResponse } from '@/types/api'

import LeaderboardPage from '../LeaderboardPage.vue'

const leaderboard = vi.fn()
vi.mock('@/api/users', () => ({
  usersApi: { leaderboard: (...a: unknown[]) => leaderboard(...a) },
}))
const cities = vi.fn()
vi.mock('@/api/catalog', () => ({ catalogApi: { cities: () => cities() } }))

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

function entry(rank: number, id: number, points: number): LeaderboardEntry {
  return { rank, user: { id, displayName: `Người ${id}`, avatarUrl: null }, points }
}

/** 25 người: hạng 1 = 2.480 điểm, giảm dần; hạng 4 và 5 bằng điểm (cùng hạng 4). */
function board(): LeaderboardEntry[] {
  const rows = [entry(1, 1, 2480), entry(2, 2, 2215), entry(3, 3, 1990)]
  rows.push(entry(4, 4, 1640), entry(4, 5, 1640))
  for (let i = 6; i <= 25; i++) rows.push(entry(i, i, 1600 - i * 10))
  return rows
}

let router: Router

async function mountPage(query: Record<string, string> = {}): Promise<VueWrapper> {
  const Empty = { render: () => null }
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Empty },
      { path: '/leaderboard', name: 'leaderboard', component: Empty },
      { path: '/users/:id', name: 'profile', component: Empty },
    ],
  })
  await router.push({ path: '/leaderboard', query })
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(LeaderboardPage, {
    global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  setActivePinia(createPinia())
  leaderboard.mockReset().mockResolvedValue(board())
  cities.mockReset().mockResolvedValue([
    { name: 'Đà Lạt', placeCount: 40 },
    { name: 'Hà Nội', placeCount: 35 },
  ])
})

describe('LeaderboardPage (P08)', () => {
  it('mặc định 30 ngày toàn quốc; bục 3 ô + danh sách 20 đầu, "Xem thêm"', async () => {
    const wrapper = await mountPage()
    expect(leaderboard).toHaveBeenCalledWith('MONTH', null, 100)
    const pods = wrapper.findAll('.pod')
    expect(pods).toHaveLength(3)
    expect(pods[0]!.classes()).toContain('pod--first')
    expect(pods[0]!.text()).toContain('Người 1')
    expect(pods[0]!.text()).toContain('2.480 điểm')
    const rows = wrapper.findAll('ol.lb > li')
    expect(rows).toHaveLength(17) // hạng 4 → 20
    // Cùng điểm cùng hạng
    expect(rows[0]!.get('.lb__rank').text()).toBe('4')
    expect(rows[1]!.get('.lb__rank').text()).toBe('4')
    expect(rows[0]!.get('a').attributes('href')).toBe('/users/4')

    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Xem thêm')!
      .trigger('click')
    expect(wrapper.findAll('ol.lb > li')).toHaveLength(22)
    expect(wrapper.text()).not.toContain('Xem thêm')
  })

  it('bộ lọc kỳ + khu vực nằm trên URL và gọi lại API', async () => {
    const wrapper = await mountPage()
    expect(wrapper.findAll('select option').map((o) => o.text())).toEqual([
      'Toàn quốc',
      'Đà Lạt',
      'Hà Nội',
    ])
    await wrapper
      .findAll('button.ls-fchip')
      .find((b) => b.text() === '7 ngày')!
      .trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ period: 'WEEK' })
    await wrapper.get('select').setValue('Đà Lạt')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ period: 'WEEK', city: 'Đà Lạt' })
  })

  it('đọc bộ lọc từ URL (link chia sẻ / F5)', async () => {
    const wrapper = await mountPage({ period: 'ALL', city: 'Hà Nội' })
    expect(leaderboard).toHaveBeenCalledWith('ALL', 'Hà Nội', 100)
    const pressed = wrapper.findAll('button.ls-fchip').map((b) => b.attributes('aria-pressed'))
    expect(pressed).toEqual(['false', 'false', 'true'])
    expect(wrapper.text()).toContain('Điểm đóng góp từ trước tới nay · Hà Nội')
  })

  it('hàng của tôi trong top: ghim cuối, còn bao nhiêu điểm để bằng hạng trên', async () => {
    // Tôi (id 42) ở hạng 6 = 1.540 điểm
    leaderboard.mockResolvedValue(
      board().map((e) => (e.user.id === 6 ? { ...e, user: { ...e.user, id: 42 } } : e)),
    )
    useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
    const wrapper = await mountPage()
    const pinned = wrapper.get('.lb--pinned')
    expect(pinned.text()).toContain('Bạn')
    // Hạng 6 = 1.540 điểm; người ngay trên (hạng 4) 1.640 → còn 100 điểm để lên hạng 4
    expect(pinned.text()).toContain('Còn 100 điểm để lên hạng 4')
    expect(wrapper.find('ol.lb > li[aria-current="true"]').text()).toContain('(Bạn)')
  })

  it('đứng đầu / ngoài top / chưa ai có điểm', async () => {
    useAuthStore().$patch({ user: { ...ME, id: 1 }, accessToken: 'token' })
    let wrapper = await mountPage()
    expect(wrapper.get('.lb--pinned').text()).toContain('Bạn đang dẫn đầu bảng!')
    wrapper.unmount()

    useAuthStore().$patch({ user: { ...ME, id: 999 } })
    wrapper = await mountPage()
    expect(wrapper.get('.lb--pinned').text()).toContain('Bạn chưa vào top 100')
    wrapper.unmount()

    leaderboard.mockResolvedValue([])
    wrapper = await mountPage({ city: 'Đà Lạt' })
    expect(wrapper.text()).toContain('Chưa có ai có điểm 30 ngày qua ở Đà Lạt')
  })
})
