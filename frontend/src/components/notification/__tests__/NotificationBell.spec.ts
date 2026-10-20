import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import type { AppNotification, MeResponse } from '@/types/api'

import NotificationBell from '../NotificationBell.vue'

const { api } = vi.hoisted(() => ({
  api: {
    list: vi.fn(),
    unreadCount: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
  },
}))
vi.mock('@/api/notifications', () => ({ notificationsApi: api }))
vi.mock('@/api/notificationSocket', () => ({
  createNotificationSocket: () => ({ start: vi.fn(), stop: vi.fn(() => Promise.resolve()) }),
}))
const notifyError = vi.fn()
vi.mock('@/plugins/toast', () => ({
  useNotify: () => ({ success: vi.fn(), error: notifyError, info: vi.fn() }),
}))

const place = { placeId: 3, placeName: 'Cà phê Mộc', placeSlug: 'ca-phe-moc' }

function note(id: number, readAt: string | null, type: AppNotification['type']): AppNotification {
  return { id, type, data: { ...place, reviewId: 9 }, readAt, createdAt: new Date().toISOString() }
}

async function setup(unread = 12) {
  setActivePinia(createPinia())
  api.unreadCount.mockResolvedValue(unread)
  api.list.mockResolvedValue({
    items: [note(2, null, 'OWNER_REPLIED'), note(1, '2026-10-18T00:00:00Z', 'CLAIM_SUBMITTED')],
    nextCursor: 'c1',
  })
  api.markRead.mockResolvedValue(undefined)
  api.markAllRead.mockResolvedValue(undefined)
  const auth = useAuthStore()
  auth.accessToken = 'token'
  auth.user = { id: 42, displayName: 'Minh Anh' } as MeResponse
  useNotificationStore().init()

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { render: () => null } },
      { path: '/places/:slug', name: 'place', component: { render: () => null } },
    ],
  })
  await router.push('/')
  const wrapper = mount(NotificationBell, {
    global: { plugins: [router] },
    attachTo: document.body,
  })
  await flushPromises()
  return { wrapper, router }
}

describe('NotificationBell', () => {
  beforeEach(() => vi.clearAllMocks())

  it('số trên chuông tối đa 9+, nhãn đọc được số thật; chưa mở thì chưa tải danh sách', async () => {
    const { wrapper } = await setup()
    const bell = wrapper.get('button.ls-iconbtn')

    expect(bell.get('.dot').text()).toBe('9+')
    expect(bell.attributes('aria-label')).toBe('Thông báo, 12 mới')
    expect(bell.attributes('aria-expanded')).toBe('false')
    expect(api.list).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('mở bảng: dòng chưa đọc có "· Mới" (không chỉ bằng màu), bấm dòng có đích → đánh dấu đọc, chuyển trang, đóng bảng', async () => {
    const { wrapper, router } = await setup(1)
    await wrapper.get('button.ls-iconbtn').trigger('click')
    await flushPromises()

    const rows = wrapper.findAll('.ls-note')
    expect(rows).toHaveLength(2)
    expect(rows[0]!.classes()).toContain('ls-note--new')
    expect(rows[0]!.get('time').text()).toContain('· Mới')
    expect(rows[1]!.get('time').text()).not.toContain('Mới')

    await rows[0]!.get('a').trigger('click')
    await flushPromises()

    expect(api.markRead).toHaveBeenCalledExactlyOnceWith(2)
    expect(router.currentRoute.value.fullPath).toBe('/places/ca-phe-moc')
    expect(wrapper.get('button.ls-iconbtn').attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('.dot').exists()).toBe(false)
    wrapper.unmount()
  })

  it('Escape đóng bảng và trả focus về chuông', async () => {
    const { wrapper } = await setup()
    const bell = wrapper.get('button.ls-iconbtn')
    await bell.trigger('click')

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()

    expect(bell.attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(bell.element)
    wrapper.unmount()
  })

  it('lọc Chưa đọc gọi máy chủ; Xem cũ hơn tải tiếp theo cursor', async () => {
    const { wrapper } = await setup()
    await wrapper.get('button.ls-iconbtn').trigger('click')
    await flushPromises()

    const more = wrapper.findAll('button').find((b) => b.text() === 'Xem cũ hơn')!
    api.list.mockResolvedValueOnce({ items: [], nextCursor: null })
    await more.trigger('click')
    await flushPromises()
    expect(api.list).toHaveBeenLastCalledWith('c1', 10, false)

    const unread = wrapper.findAll('.ls-fchip').find((b) => b.text().startsWith('Chưa đọc'))!
    api.list.mockResolvedValueOnce({ items: [], nextCursor: null })
    await unread.trigger('click')
    await flushPromises()
    expect(api.list).toHaveBeenLastCalledWith(null, 10, true)
    expect(unread.attributes('aria-pressed')).toBe('true')
    expect(wrapper.text()).toContain('Bạn đã đọc hết thông báo.')
    wrapper.unmount()
  })

  it('đọc tất cả lỗi → báo lỗi, số chưa đọc giữ nguyên', async () => {
    const { wrapper } = await setup(3)
    await wrapper.get('button.ls-iconbtn').trigger('click')
    await flushPromises()

    api.markAllRead.mockRejectedValueOnce(new Error('500'))
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Đánh dấu đã đọc tất cả')!
      .trigger('click')
    await flushPromises()

    expect(notifyError).toHaveBeenCalledOnce()
    expect(wrapper.get('.dot').text()).toBe('3')
    wrapper.unmount()
  })
})
