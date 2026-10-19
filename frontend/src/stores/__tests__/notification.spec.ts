import { flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { NotificationSocketOptions } from '@/api/notificationSocket'
import type { AppNotification, CursorPage, MeResponse } from '@/types/api'

import { useAuthStore } from '../auth'
import { useNotificationStore } from '../notification'

const { api, socket } = vi.hoisted(() => ({
  api: {
    list: vi.fn<(cursor?: string | null, limit?: number) => Promise<CursorPage<AppNotification>>>(),
    unreadCount: vi.fn<() => Promise<number>>(),
    markRead: vi.fn<(id: number) => Promise<void>>(),
    markAllRead: vi.fn<() => Promise<void>>(),
  },
  socket: {
    options: null as NotificationSocketOptions | null,
    start: vi.fn(),
    stop: vi.fn(() => Promise.resolve()),
  },
}))
vi.mock('@/api/notifications', () => ({ notificationsApi: api }))
vi.mock('@/api/notificationSocket', () => ({
  createNotificationSocket: (opts: NotificationSocketOptions) => {
    socket.options = opts
    return { start: socket.start, stop: socket.stop }
  },
}))
const { start, stop } = socket

function notification(id: number, readAt: string | null = null): AppNotification {
  return {
    id,
    type: 'OWNER_REPLIED',
    data: { placeId: 1, placeName: 'Cà phê Mộc', placeSlug: 'ca-phe-moc', reviewId: 9 },
    readAt,
    createdAt: '2026-10-19T03:00:00Z',
  }
}

function signIn(id = 7): void {
  const auth = useAuthStore()
  auth.accessToken = 'token'
  auth.user = { id, displayName: 'Minh Anh' } as MeResponse
}

describe('notification store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    socket.options = null
    api.unreadCount.mockResolvedValue(3)
    api.list.mockResolvedValue({
      items: [notification(12), notification(11, 'x')],
      nextCursor: 'c1',
    })
    api.markRead.mockResolvedValue()
    api.markAllRead.mockResolvedValue()
  })

  it('khách: không đọc số, không mở kết nối', async () => {
    useNotificationStore().init()
    await flushPromises()

    expect(api.unreadCount).not.toHaveBeenCalled()
    expect(start).not.toHaveBeenCalled()
  })

  it('đăng nhập → đọc số chưa đọc + mở kết nối; danh sách chờ tới khi mở bảng', async () => {
    const store = useNotificationStore()
    store.init()
    signIn()
    await flushPromises()

    expect(store.unreadCount).toBe(3)
    expect(start).toHaveBeenCalledOnce()
    expect(api.list).not.toHaveBeenCalled()

    await store.ensureLoaded()
    await store.ensureLoaded()
    expect(api.list).toHaveBeenCalledOnce()
    expect(store.items.map((n) => n.id)).toEqual([12, 11])
    expect(store.hasMore).toBe(true)
  })

  it('đăng xuất → đóng kết nối, xoá thông báo của phiên cũ', async () => {
    const store = useNotificationStore()
    store.init()
    signIn()
    await flushPromises()
    await store.ensureLoaded()

    useAuthStore().clearSession()
    await flushPromises()

    expect(stop).toHaveBeenCalledOnce()
    expect(store.items).toEqual([])
    expect(store.unreadCount).toBe(0)
    expect(store.loaded).toBe(false)
  })

  it('kết quả request của phiên cũ về muộn bị bỏ', async () => {
    let resolveCount: (n: number) => void = () => {}
    api.unreadCount.mockReturnValueOnce(new Promise((resolve) => (resolveCount = resolve)))
    const store = useNotificationStore()
    store.init()
    signIn()
    await flushPromises()

    useAuthStore().clearSession()
    await flushPromises()
    resolveCount(42)
    await flushPromises()

    expect(store.unreadCount).toBe(0)
  })

  it('khung đẩy tới: thêm lên đầu, tăng số chưa đọc, bỏ khung trùng', async () => {
    const store = useNotificationStore()
    store.init()
    signIn()
    await flushPromises()
    await store.ensureLoaded()

    socket.options!.onNotification(notification(13))
    socket.options!.onNotification(notification(13))

    expect(store.items.map((n) => n.id)).toEqual([13, 12, 11])
    expect(store.unreadCount).toBe(4)
  })

  it('nối lại sau khi mất mạng → đọc lại số và trang đầu; lần kết nối đầu thì không', async () => {
    const store = useNotificationStore()
    store.init()
    signIn()
    await flushPromises()
    await store.ensureLoaded()

    socket.options!.onConnected()
    await flushPromises()
    expect(api.unreadCount).toHaveBeenCalledOnce()
    expect(api.list).toHaveBeenCalledOnce()

    api.unreadCount.mockResolvedValue(5)
    socket.options!.onConnected()
    await flushPromises()
    expect(store.unreadCount).toBe(5)
    expect(api.list).toHaveBeenCalledTimes(2)
  })

  it('đánh dấu đã đọc cập nhật ngay; lỗi thì trả lại và đọc lại số', async () => {
    const store = useNotificationStore()
    store.init()
    signIn()
    await flushPromises()
    await store.ensureLoaded()

    await store.markRead(store.items[0]!)
    expect(api.markRead).toHaveBeenCalledExactlyOnceWith(12)
    expect(store.items[0]!.readAt).not.toBeNull()
    expect(store.unreadCount).toBe(2)

    // đã đọc → không gọi lại
    await store.markRead(store.items[0]!)

    socket.options!.onNotification(notification(14))
    api.markRead.mockRejectedValueOnce(new Error('500'))
    api.unreadCount.mockResolvedValue(3)
    await store.markRead(store.items[0]!)
    expect(store.items[0]!.readAt).toBeNull()
    expect(store.unreadCount).toBe(3)
  })

  it('đọc tất cả: về 0 ngay; lỗi thì khôi phục và báo cho giao diện', async () => {
    const store = useNotificationStore()
    store.init()
    signIn()
    await flushPromises()
    await store.ensureLoaded()

    api.markAllRead.mockRejectedValueOnce(new Error('500'))
    await expect(store.markAllRead()).rejects.toThrow()
    expect(store.unreadCount).toBe(3)
    expect(store.items[0]!.readAt).toBeNull()

    await store.markAllRead()
    expect(store.unreadCount).toBe(0)
    expect(store.items.every((n) => n.readAt)).toBe(true)
  })

  it('tải thêm nối tiếp theo cursor, bỏ dòng trùng với khung vừa đẩy', async () => {
    const store = useNotificationStore()
    store.init()
    signIn()
    await flushPromises()
    await store.ensureLoaded()

    api.list.mockResolvedValueOnce({
      items: [notification(11, 'x'), notification(10)],
      nextCursor: null,
    })
    await store.loadMore()

    expect(api.list).toHaveBeenLastCalledWith('c1', 10, false)
    expect(store.items.map((n) => n.id)).toEqual([12, 11, 10])
    expect(store.hasMore).toBe(false)
  })

  it('lọc Chưa đọc ở máy chủ; trang của bộ lọc cũ về muộn bị bỏ', async () => {
    const store = useNotificationStore()
    store.init()
    signIn()
    await flushPromises()

    let resolveAll: (page: CursorPage<AppNotification>) => void = () => {}
    api.list.mockReturnValueOnce(new Promise((resolve) => (resolveAll = resolve)))
    void store.ensureLoaded()
    api.list.mockResolvedValueOnce({ items: [notification(12)], nextCursor: null })
    await store.setFilter('unread')
    resolveAll({ items: [notification(12), notification(11, 'x')], nextCursor: 'c1' })
    await flushPromises()

    expect(api.list).toHaveBeenLastCalledWith(null, 10, true)
    expect(store.items.map((n) => n.id)).toEqual([12])
    expect(store.hasMore).toBe(false)
    expect(store.loading).toBe(false)
  })
})
