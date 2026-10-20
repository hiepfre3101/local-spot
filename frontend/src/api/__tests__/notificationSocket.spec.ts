import type { Client, IMessage, messageCallbackType } from '@stomp/stompjs'
import { describe, expect, it, vi } from 'vitest'

import {
  brokerUrl,
  createNotificationSocket,
  NOTIFICATION_DESTINATION,
  type NotificationSocketOptions,
} from '../notificationSocket'

/** Client giả: giữ các callback để test gọi như thư viện thật. */
function fakeClient() {
  const subscriptions: { destination: string; callback: messageCallbackType }[] = []
  const client = {
    connectHeaders: {} as Record<string, string>,
    beforeConnect: async () => {},
    onConnect: () => {},
    onStompError: () => {},
    activate: vi.fn(),
    deactivate: vi.fn(() => Promise.resolve()),
    subscribe: vi.fn((destination: string, callback: messageCallbackType) => {
      subscriptions.push({ destination, callback })
    }),
  }
  return { client, subscriptions, asClient: () => client as unknown as Client }
}

function options(overrides: Partial<NotificationSocketOptions> = {}): NotificationSocketOptions {
  return {
    getAccessToken: () => 'token-1',
    refresh: vi.fn(() => Promise.resolve('token-2')),
    onNotification: vi.fn(),
    onConnected: vi.fn(),
    ...overrides,
  }
}

describe('notificationSocket', () => {
  it('đi cùng origin với trang, https → wss', () => {
    expect(brokerUrl({ protocol: 'http:', host: 'localhost:5173' })).toBe('ws://localhost:5173/ws')
    expect(brokerUrl({ protocol: 'https:', host: 'localspot.vn' })).toBe('wss://localspot.vn/ws')
  })

  it('gửi token trong khung CONNECT, không làm mới khi đang có token', async () => {
    const fake = fakeClient()
    const opts = options()
    createNotificationSocket(opts, fake.asClient)

    await fake.client.beforeConnect()

    expect(fake.client.connectHeaders).toEqual({ Authorization: 'Bearer token-1' })
    expect(opts.refresh).not.toHaveBeenCalled()
  })

  it('máy chủ từ chối CONNECT (token hết hạn) → lần nối lại làm mới phiên trước', async () => {
    const fake = fakeClient()
    const opts = options()
    createNotificationSocket(opts, fake.asClient)

    fake.client.onStompError()
    await fake.client.beforeConnect()

    expect(opts.refresh).toHaveBeenCalledOnce()
    expect(fake.client.connectHeaders).toEqual({ Authorization: 'Bearer token-2' })
  })

  it('làm mới thất bại (hết phiên) → dừng hẳn, không thử lại mãi', async () => {
    const fake = fakeClient()
    createNotificationSocket(
      options({ getAccessToken: () => null, refresh: () => Promise.resolve(null) }),
      fake.asClient,
    )

    await fake.client.beforeConnect()

    expect(fake.client.deactivate).toHaveBeenCalledOnce()
  })

  it('kết nối xong → đăng ký kênh cá nhân, chuyển từng khung thành thông báo, bỏ qua khung hỏng', () => {
    const fake = fakeClient()
    const opts = options()
    createNotificationSocket(opts, fake.asClient)

    fake.client.onConnect()
    const [subscription] = fake.subscriptions
    subscription!.callback({ body: '{"id":5,"type":"OWNER_REPLIED"}' } as IMessage)
    subscription!.callback({ body: 'not json' } as IMessage)

    expect(subscription!.destination).toBe(NOTIFICATION_DESTINATION)
    expect(opts.onConnected).toHaveBeenCalledOnce()
    expect(opts.onNotification).toHaveBeenCalledExactlyOnceWith({ id: 5, type: 'OWNER_REPLIED' })
  })
})
