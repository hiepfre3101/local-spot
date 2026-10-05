import { AxiosError, AxiosHeaders, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'

import { connectAuth, http, type AuthBridge } from '../http'

/** Adapter giả: trả 401 khi token không phải `fresh`, ghi lại các request đã gửi. */
function fakeServer(validToken = 'fresh') {
  const seen: { url?: string; auth?: string }[] = []
  const adapter: AxiosAdapter = async (config: InternalAxiosRequestConfig) => {
    const auth = AxiosHeaders.from(config.headers).get('Authorization') as string | undefined
    seen.push({ url: config.url, auth })
    const ok = config.url?.startsWith('/auth/') || auth === `Bearer ${validToken}`
    const response = {
      data: ok ? { ok: true } : { code: 'UNAUTHORIZED' },
      status: ok ? 200 : 401,
      statusText: '',
      headers: {},
      config,
    }
    if (!ok) {
      throw new AxiosError('401', 'ERR_BAD_REQUEST', config, null, response)
    }
    return response
  }
  return { adapter, seen }
}

describe('http — làm mới phiên khi 401', () => {
  let token: string | null
  /** Số lần làm mới thật sự (không phải số lần được yêu cầu). */
  let refreshes: number
  let bridge: AuthBridge & {
    refresh: Mock<AuthBridge['refresh']>
    onSessionExpired: Mock<AuthBridge['onSessionExpired']>
  }

  beforeEach(() => {
    token = 'stale'
    refreshes = 0
    let pending: Promise<string | null> | null = null
    bridge = {
      getAccessToken: () => token,
      // Gộp các lời gọi đồng thời như store thật
      refresh: vi.fn<AuthBridge['refresh']>(() => {
        if (!pending) {
          refreshes += 1
        }
        pending ??= new Promise<string | null>((resolve) =>
          setTimeout(() => {
            token = 'fresh'
            resolve('fresh')
          }, 5),
        ).finally(() => (pending = null))
        return pending
      }),
      onSessionExpired: vi.fn<AuthBridge['onSessionExpired']>(),
    }
    connectAuth(bridge)
  })

  afterEach(() => connectAuth(null))

  it('gắn access token và gửi lại request sau một lần làm mới cho nhiều request đồng thời', async () => {
    const server = fakeServer()
    http.defaults.adapter = server.adapter

    const results = await Promise.all([
      http.get('/me'),
      http.get('/admin/users'),
      http.get('/me/places'),
    ])

    expect(results.every((r) => r.status === 200)).toBe(true)
    // Mỗi request 401 đều xin làm mới, nhưng chỉ một lần làm mới thật được thực hiện
    expect(bridge.refresh).toHaveBeenCalledTimes(3)
    expect(refreshes).toBe(1)
    expect(server.seen.filter((r) => r.auth === 'Bearer stale')).toHaveLength(3)
    expect(server.seen.filter((r) => r.auth === 'Bearer fresh')).toHaveLength(3)
  })

  it('làm mới thất bại → báo hết phiên, không lặp vô hạn', async () => {
    http.defaults.adapter = fakeServer().adapter
    bridge.refresh.mockResolvedValueOnce(null)

    await expect(http.get('/me')).rejects.toBeInstanceOf(AxiosError)
    expect(bridge.onSessionExpired).toHaveBeenCalledWith('expired')
  })

  it('không mang token và không tự làm mới với endpoint đăng nhập / làm mới', async () => {
    const seen: { url?: string; auth?: string }[] = []
    http.defaults.adapter = async (config) => {
      seen.push({
        url: config.url,
        auth: AxiosHeaders.from(config.headers).get('Authorization') as string,
      })
      throw new AxiosError('401', 'ERR_BAD_REQUEST', config, null, {
        data: { code: 'INVALID_CREDENTIALS' },
        status: 401,
        statusText: '',
        headers: {},
        config,
      })
    }

    await expect(http.post('/auth/login', {})).rejects.toBeInstanceOf(AxiosError)
    expect(seen[0]?.auth).toBeUndefined()
    expect(bridge.refresh).not.toHaveBeenCalled()
  })

  it('tài khoản bị khóa → báo hết phiên ngay, không làm mới', async () => {
    http.defaults.adapter = async (config) => {
      throw new AxiosError('401', 'ERR_BAD_REQUEST', config, null, {
        data: { code: 'ACCOUNT_LOCKED' },
        status: 401,
        statusText: '',
        headers: {},
        config,
      })
    }

    await expect(http.get('/me')).rejects.toBeInstanceOf(AxiosError)
    expect(bridge.refresh).not.toHaveBeenCalled()
    expect(bridge.onSessionExpired).toHaveBeenCalledWith('locked')
  })
})
