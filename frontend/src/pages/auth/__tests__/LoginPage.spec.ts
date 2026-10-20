import { flushPromises, mount } from '@vue/test-utils'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import LoginPage from '../LoginPage.vue'

const login = vi.fn()

vi.mock('@/api/auth', () => ({
  authApi: { login: (body: unknown) => login(body), refresh: vi.fn(), logout: vi.fn() },
}))

function apiError(status: number, data: unknown): AxiosError {
  const config = { headers: {} } as InternalAxiosRequestConfig
  return new AxiosError('x', 'ERR', config, null, {
    data,
    status,
    statusText: '',
    headers: {},
    config,
  })
}

async function mountAt(path: string) {
  const Empty = { render: () => null }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Empty },
      { path: '/login', name: 'login', component: LoginPage },
      { path: '/forgot-password', name: 'forgot-password', component: Empty },
      { path: '/settings/password', name: 'settings-password', component: Empty },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(LoginPage, { global: { plugins: [router] } })
  return { wrapper, router }
}

async function fillAndSubmit(wrapper: Awaited<ReturnType<typeof mountAt>>['wrapper']) {
  await wrapper.get('input[name="email"]').setValue('ma@localspot.test')
  await wrapper.get('input[name="password"]').setValue('LocalSpot2026')
  await wrapper.get('form').trigger('submit')
  // vee-validate kiểm bất đồng bộ qua nhiều tick — chờ tới khi gửi xong (nút hết trạng thái bận)
  await vi.waitFor(() =>
    expect(wrapper.get('button[type="submit"]').attributes('aria-busy')).toBeUndefined(),
  )
  await flushPromises()
}

describe('LoginPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    login.mockReset()
  })

  it('kiểm dữ liệu trước khi gọi API', async () => {
    const { wrapper } = await mountAt('/login')
    await wrapper.get('form').trigger('submit')

    await vi.waitFor(() => expect(wrapper.text()).toContain('✕ Nhập email.'))
    expect(login).not.toHaveBeenCalled()
    expect(wrapper.get('input[name="email"]').attributes('aria-invalid')).toBe('true')
  })

  it('sai thông tin → một câu chung gắn vào ô mật khẩu (không lộ email nào tồn tại)', async () => {
    login.mockRejectedValue(apiError(401, { code: 'INVALID_CREDENTIALS', detail: 'x' }))
    const { wrapper } = await mountAt('/login')

    await fillAndSubmit(wrapper)

    expect(wrapper.text()).toContain('✕ Email hoặc mật khẩu chưa đúng.')
    expect(wrapper.get('input[name="password"]').attributes('aria-invalid')).toBe('true')
  })

  it('quá số lần thử → hiện câu của máy chủ (có thời gian chờ)', async () => {
    login.mockRejectedValue(
      apiError(429, {
        code: 'TOO_MANY_REQUESTS',
        detail: 'Bạn thử quá nhiều lần. Thử lại sau 15 phút.',
      }),
    )
    const { wrapper } = await mountAt('/login')

    await fillAndSubmit(wrapper)

    expect(wrapper.get('[role="alert"]').text()).toContain('Thử lại sau 15 phút.')
  })

  it('thành công → quay lại trang trong ?redirect=', async () => {
    login.mockResolvedValue({
      accessToken: 't',
      expiresIn: 900,
      user: { id: 1, displayName: 'MA', permissions: [], roles: ['USER'] },
    })
    const { wrapper, router } = await mountAt('/login?redirect=/settings/password')

    await fillAndSubmit(wrapper)

    expect(login).toHaveBeenCalledWith({ email: 'ma@localspot.test', password: 'LocalSpot2026' })
    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/settings/password'))
  })
})
