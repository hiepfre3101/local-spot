import { Client, ReconnectionTimeMode, type IMessage } from '@stomp/stompjs'

import type { AppNotification } from '@/types/api'

/** Kênh cá nhân — máy chủ chỉ cho SUBSCRIBE đúng đường này (`StompAuthInterceptor`). */
export const NOTIFICATION_DESTINATION = '/user/queue/notifications'

export interface NotificationSocketOptions {
  /** Access token đang có trong bộ nhớ (store auth). */
  getAccessToken(): string | null
  /** Làm mới phiên (dùng chung với axios — gộp lời gọi đồng thời); `null` = không còn phiên. */
  refresh(): Promise<string | null>
  onNotification(notification: AppNotification): void
  /** Mỗi lần kết nối xong (cả lần đầu lẫn sau khi nối lại) — thông báo lúc mất kết nối không được đẩy lại, phải đọc qua REST. */
  onConnected(): void
}

export interface NotificationSocket {
  start(): void
  stop(): Promise<void>
}

/** Cùng origin với trang: dev đi qua proxy `/ws` của Vite, production qua nginx — cookie và CORS giống REST. */
export function brokerUrl(location: Pick<Location, 'protocol' | 'host'> = window.location): string {
  return `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`
}

/**
 * Kết nối STOMP nhận thông báo realtime (FR-27, chốt 2026-10-16: WebSocket thuần, không SockJS).
 *
 * - **Token ở khung CONNECT**, không ở URL (token trên URL lọt vào log truy cập) — đọc lại ngay trước mỗi lần kết nối
 *   nên lần nối lại dùng token mới nhất mà axios vừa làm mới.
 * - Máy chủ từ chối CONNECT (token 15 phút đã hết hạn trong lúc mất mạng) → lần thử sau làm mới phiên trước. Làm mới
 *   thất bại = hết phiên → dừng hẳn; việc đưa về trang đăng nhập do luồng 401 của axios lo, không lặp lại ở đây.
 * - Nối lại lùi dần 2 s → 60 s: máy chủ khởi động lại không bị mọi tab dội cùng lúc.
 */
export function createNotificationSocket(
  options: NotificationSocketOptions,
  createClient: () => Client = () => new Client(),
): NotificationSocket {
  const client = createClient()
  let rejected = false

  client.brokerURL = brokerUrl()
  client.reconnectDelay = 2_000
  client.reconnectTimeMode = ReconnectionTimeMode.EXPONENTIAL
  client.maxReconnectDelay = 60_000

  client.beforeConnect = async () => {
    let token = rejected ? null : options.getAccessToken()
    if (!token) {
      token = await options.refresh().catch(() => null)
    }
    if (!token) {
      await client.deactivate()
      return
    }
    rejected = false
    client.connectHeaders = { Authorization: `Bearer ${token}` }
  }

  client.onConnect = () => {
    client.subscribe(NOTIFICATION_DESTINATION, (message: IMessage) => {
      try {
        options.onNotification(JSON.parse(message.body) as AppNotification)
      } catch {
        // khung hỏng: bỏ qua — danh sách sẽ khớp lại ở lần đọc REST sau
      }
    })
    options.onConnected()
  }

  client.onStompError = () => {
    rejected = true
  }

  return {
    start: () => client.activate(),
    stop: () => client.deactivate(),
  }
}
