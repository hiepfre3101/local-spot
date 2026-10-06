import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'

import { resolveNavigation, type GuardAuth } from '../guards'
import { safeRedirect } from '../redirect'

const Empty = { render: () => null }

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: Empty },
  { path: '/login', name: 'login', component: Empty, meta: { guestOnly: true } },
  { path: '/403', name: 'forbidden', component: Empty },
  { path: '/settings/password', name: 'settings', component: Empty, meta: { requiresAuth: true } },
  {
    path: '/admin',
    component: Empty,
    meta: { anyPermission: ['place:approve', 'user:view'] },
    children: [
      {
        path: 'users',
        name: 'admin-users',
        component: Empty,
        meta: { anyPermission: ['user:view'] },
      },
    ],
  },
]

function auth(permissions: string[] | null): GuardAuth {
  return {
    init: async () => undefined,
    isAuthenticated: permissions !== null,
    canAny: (required) => required.some((p) => permissions?.includes(p) ?? false),
  }
}

async function navigate(path: string, who: GuardAuth) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  return resolveNavigation(router.resolve(path), who)
}

describe('resolveNavigation', () => {
  it('khách vào trang cần đăng nhập → /login kèm redirect quay lại đúng trang', async () => {
    expect(await navigate('/settings/password?x=1', auth(null))).toEqual({
      name: 'login',
      query: { redirect: '/settings/password?x=1' },
    })
    expect(await navigate('/admin/users', auth(null))).toMatchObject({ name: 'login' })
  })

  it('đã đăng nhập mở trang chỉ dành cho khách → về redirect nội bộ hoặc trang chủ', async () => {
    expect(await navigate('/login?redirect=/settings/password', auth([]))).toBe(
      '/settings/password',
    )
    expect(await navigate('/login?redirect=//evil.com', auth([]))).toBe('/')
  })

  it('xét quyền ở mọi cấp route: kiểm duyệt viên vào được /admin nhưng không vào quản lý người dùng', async () => {
    expect(await navigate('/admin/users', auth(['place:approve']))).toEqual({ name: 'forbidden' })
    expect(await navigate('/admin/users', auth(['user:view']))).toBe(true)
    expect(await navigate('/admin/users', auth(['review:create']))).toEqual({ name: 'forbidden' })
  })

  it('trang công khai luôn vào được', async () => {
    expect(await navigate('/', auth(null))).toBe(true)
  })
})

describe('safeRedirect', () => {
  it.each([
    ['/places/pho-thin?tab=reviews', '/places/pho-thin?tab=reviews'],
    ['//evil.com', '/'],
    ['https://evil.com', '/'],
    ['/\\evil.com', '/'],
    [undefined, '/'],
    [['/a', '/b'], '/'],
  ])('%s → %s', (input, expected) => {
    expect(safeRedirect(input)).toBe(expected)
  })
})
