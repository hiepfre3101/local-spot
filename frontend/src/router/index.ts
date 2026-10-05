import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { STAFF_PERMISSIONS, Permission } from '@/auth/permissions'
import { visibleAdminNav } from '@/layouts/adminNav'
import { useAuthStore } from '@/stores/auth'

import { installGuards } from './guards'

/**
 * Route theo docs/design/sitemap.md. Mỗi nhóm là một layout (route cha) — trang con không tự lặp lại header / sidebar.
 * Route của module chưa làm chưa khai báo; điều hướng (header, sidebar) chỉ hiện mục đã có route.
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/pages/HomePage.vue') },
      {
        path: 'settings/password',
        name: 'settings-password',
        component: () => import('@/pages/settings/PasswordSettingsPage.vue'),
        meta: { title: 'Đổi mật khẩu', requiresAuth: true },
      },
      {
        path: '403',
        name: 'forbidden',
        component: () => import('@/pages/errors/ForbiddenPage.vue'),
        meta: { title: 'Không có quyền' },
      },
    ],
  },
  {
    path: '/',
    component: () => import('@/layouts/AuthLayout.vue'),
    children: [
      {
        path: 'login',
        name: 'login',
        component: () => import('@/pages/auth/LoginPage.vue'),
        meta: { title: 'Đăng nhập', guestOnly: true },
      },
      {
        path: 'register',
        name: 'register',
        component: () => import('@/pages/auth/RegisterPage.vue'),
        meta: { title: 'Đăng ký', guestOnly: true },
      },
      {
        // Không có token: "Kiểm tra hộp thư" sau đăng ký; có ?token=: xác thực (UC02)
        path: 'verify-email',
        name: 'verify-email',
        component: () => import('@/pages/auth/VerifyEmailPage.vue'),
        meta: { title: 'Xác thực email' },
      },
      {
        path: 'forgot-password',
        name: 'forgot-password',
        component: () => import('@/pages/auth/ForgotPasswordPage.vue'),
        meta: { title: 'Khôi phục mật khẩu', guestOnly: true },
      },
      {
        path: 'reset-password',
        name: 'reset-password',
        component: () => import('@/pages/auth/ResetPasswordPage.vue'),
        meta: { title: 'Đặt mật khẩu mới' },
      },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { anyPermission: STAFF_PERMISSIONS },
    children: [
      {
        // Trang đầu theo vai trò: mục đầu tiên của sidebar mà người dùng có quyền (sitemap §2.3)
        path: '',
        name: 'admin',
        component: { render: () => null },
        beforeEnter: () => {
          const auth = useAuthStore()
          const first = visibleAdminNav(router, auth.canAny)[0]
          return first ? { name: first.routeName, query: first.query } : { name: 'forbidden' }
        },
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/pages/admin/AdminUsersPage.vue'),
        meta: { title: 'Người dùng & vai trò', anyPermission: [Permission.USER_VIEW] },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/layouts/DefaultLayout.vue'),
    children: [
      {
        path: '',
        name: 'not-found',
        component: () => import('@/pages/errors/NotFoundPage.vue'),
        meta: { title: 'Không tìm thấy trang' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: (_to, _from, saved) => saved ?? { top: 0 },
})

installGuards(router, () => useAuthStore())

export default router
