import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

import { createNotificationSocket, type NotificationSocket } from '@/api/notificationSocket'
import { notificationsApi } from '@/api/notifications'
import type { AppNotification } from '@/types/api'

import { useAuthStore } from './auth'

const PAGE_SIZE = 10

export type NotificationFilter = 'all' | 'unread'

/**
 * Thông báo trong ứng dụng (UC19, FR-27 — store `notification` của plan §2).
 *
 * Khác các store khác (dữ liệu API để trong cache Vue Query), danh sách thông báo nằm ở đây: nó bị **máy chủ đẩy tới**
 * qua STOMP chứ không chỉ đọc theo yêu cầu, số chưa đọc và danh sách phải đổi cùng nhau, và phải xoá sạch khi đổi phiên
 * (thông báo của người trước không được hiện cho người sau trên cùng máy).
 *
 * - Gắn theo phiên: đăng nhập → đọc số chưa đọc + mở kết nối; đăng xuất / hết phiên → đóng kết nối, xoá dữ liệu.
 * - Danh sách chỉ nạp khi mở bảng thông báo lần đầu (đa số lượt xem trang không mở).
 * - Lọc "Chưa đọc" ở máy chủ (`unreadOnly`), không lọc trên các dòng đã tải: trang đã tải có thể toàn dòng đã đọc trong
 *   khi trang sau còn dòng chưa đọc. Đánh dấu đọc trong chế độ này giữ dòng tại chỗ (không nhảy danh sách).
 * - Nối lại sau khi mất mạng → đọc lại số chưa đọc và trang đầu: khung đẩy lúc mất kết nối không được gửi lại.
 * - Đánh dấu đã đọc cập nhật ngay trên giao diện; lỗi thì đọc lại từ máy chủ cho khớp.
 */
export const useNotificationStore = defineStore('notification', () => {
  const auth = useAuthStore()

  const items = ref<AppNotification[]>([])
  const nextCursor = ref<string | null>(null)
  const loaded = ref(false)
  const loading = ref(false)
  const loadError = ref(false)
  const unreadCount = ref(0)
  const filter = ref<NotificationFilter>('all')

  const hasMore = computed(() => nextCursor.value !== null)

  let socket: NotificationSocket | null = null
  /** Tăng mỗi lần đổi phiên — kết quả của request thuộc phiên cũ bị bỏ. */
  let generation = 0
  let listRequest = 0
  let connectedOnce = false
  let started = false

  function reset(): void {
    generation++
    listRequest++
    items.value = []
    nextCursor.value = null
    loaded.value = false
    loading.value = false
    loadError.value = false
    unreadCount.value = 0
    filter.value = 'all'
    connectedOnce = false
  }

  /** Chạy `request`; bỏ kết quả nếu phiên đã đổi trong lúc chờ. */
  async function inSession<T>(request: () => Promise<T>): Promise<{ value: T } | null> {
    const current = generation
    const value = await request()
    return current === generation ? { value } : null
  }

  async function refreshCount(): Promise<void> {
    try {
      const result = await inSession(() => notificationsApi.unreadCount())
      if (result) {
        unreadCount.value = result.value
      }
    } catch {
      // giữ số cũ — chuông vẫn dùng được, lần nối lại / thao tác sau sẽ đọc lại
    }
  }

  /**
   * Đọc một trang danh sách. Chỉ request mới nhất được ghi kết quả: đổi bộ lọc khi trang của bộ lọc cũ chưa về, hoặc
   * đổi phiên, thì kết quả cũ bị bỏ (bộ đếm riêng — không đụng tới request đọc số chưa đọc).
   */
  async function fetchPage(
    cursor: string | null,
    apply: (page: { items: AppNotification[]; nextCursor: string | null }) => void,
  ): Promise<void> {
    const request = ++listRequest
    loading.value = true
    loadError.value = false
    try {
      const unreadOnly = filter.value === 'unread'
      const result = await inSession(() => notificationsApi.list(cursor, PAGE_SIZE, unreadOnly))
      if (result && request === listRequest) {
        apply(result.value)
      }
    } catch {
      if (request === listRequest) {
        loadError.value = true
      }
    } finally {
      if (request === listRequest) {
        loading.value = false
      }
    }
  }

  function loadFirstPage(): Promise<void> {
    return fetchPage(null, (page) => {
      items.value = page.items
      nextCursor.value = page.nextCursor
      loaded.value = true
    })
  }

  /** Mở bảng thông báo: nạp trang đầu nếu chưa có. */
  async function ensureLoaded(): Promise<void> {
    if (!loaded.value && !loading.value) {
      await loadFirstPage()
    }
  }

  /** Đổi Tất cả / Chưa đọc: nạp lại trang đầu theo bộ lọc mới. */
  async function setFilter(value: NotificationFilter): Promise<void> {
    if (filter.value === value) {
      return
    }
    filter.value = value
    items.value = []
    nextCursor.value = null
    loaded.value = false
    await loadFirstPage()
  }

  async function loadMore(): Promise<void> {
    if (!nextCursor.value || loading.value) {
      return
    }
    await fetchPage(nextCursor.value, (page) => {
      const known = new Set(items.value.map((n) => n.id))
      items.value.push(...page.items.filter((n) => !known.has(n.id)))
      nextCursor.value = page.nextCursor
    })
  }

  /** Khung STOMP mới — có thể trùng với dòng vừa đọc qua REST (đẩy sau commit, đọc lại khi nối lại). */
  function receive(notification: AppNotification): void {
    if (items.value.some((n) => n.id === notification.id)) {
      return
    }
    if (loaded.value) {
      items.value.unshift(notification)
    }
    if (!notification.readAt) {
      unreadCount.value++
    }
  }

  function onConnected(): void {
    // Lần đầu: số chưa đọc vừa đọc lúc đăng nhập, danh sách chưa nạp — không cần đọc lại
    if (!connectedOnce) {
      connectedOnce = true
      return
    }
    void refreshCount()
    if (loaded.value) {
      void loadFirstPage()
    }
  }

  async function markRead(notification: AppNotification): Promise<void> {
    if (notification.readAt) {
      return
    }
    const target = items.value.find((n) => n.id === notification.id) ?? notification
    target.readAt = new Date().toISOString()
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    try {
      await notificationsApi.markRead(notification.id)
    } catch {
      target.readAt = null
      await refreshCount()
    }
  }

  async function markAllRead(): Promise<void> {
    const now = new Date().toISOString()
    const changed = items.value.filter((n) => !n.readAt)
    changed.forEach((n) => (n.readAt = now))
    const previous = unreadCount.value
    unreadCount.value = 0
    try {
      await notificationsApi.markAllRead()
    } catch {
      changed.forEach((n) => (n.readAt = null))
      unreadCount.value = previous
      throw new Error('mark-all-read-failed')
    }
  }

  function connect(): void {
    socket = createNotificationSocket({
      getAccessToken: () => auth.accessToken,
      refresh: () => auth.refresh(),
      onNotification: receive,
      onConnected,
    })
    socket.start()
  }

  function disconnect(): void {
    void socket?.stop()
    socket = null
  }

  /** Gọi một lần lúc khởi động app: theo dõi phiên đăng nhập để mở / đóng kết nối. */
  function init(): void {
    if (started) {
      return
    }
    started = true
    watch(
      () => auth.user?.id ?? null,
      (userId, previousId) => {
        if (userId === previousId) {
          return
        }
        disconnect()
        reset()
        if (userId !== null) {
          void refreshCount()
          connect()
        }
      },
      { immediate: true },
    )
  }

  return {
    items,
    unreadCount,
    filter,
    loaded,
    loading,
    loadError,
    hasMore,
    init,
    ensureLoaded,
    setFilter,
    loadMore,
    markRead,
    markAllRead,
  }
})
