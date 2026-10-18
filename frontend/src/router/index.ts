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
        // P02 — bộ lọc trên query (sitemap §2.1): q, category, minRating, price, amenities, near, radius, sort
        path: 'search',
        name: 'search',
        component: () => import('@/pages/SearchPage.vue'),
        meta: { title: 'Khám phá' },
      },
      {
        // P03 — tiêu đề tab đặt theo tên địa điểm khi tải xong
        path: 'places/:slug',
        name: 'place',
        component: () => import('@/pages/PlaceDetailPage.vue'),
        props: true,
        meta: { title: 'Địa điểm' },
      },
      {
        // P04 — đã có đánh giá thì chế độ sửa (1 đánh giá / người / địa điểm, D2)
        path: 'places/:slug/review',
        name: 'write-review',
        component: () => import('@/pages/WriteReviewPage.vue'),
        props: true,
        meta: { title: 'Viết đánh giá', requiresAuth: true },
      },
      {
        // P06 — tab qua ?tab= (sitemap); id không phải số → trang không tìm thấy.
        // Phải là `\\d` trong chuỗi JS: viết `\d` thì JS bỏ dấu `\`, mẫu thành `(d+)` và URL thật /users/16 (nút Quay
        // lại, F5) rơi vào trang 404 — điều hướng bằng tên không kiểm mẫu nên không lộ ra.
        path: 'users/:id(\\d+)',
        name: 'profile',
        component: () => import('@/pages/ProfilePage.vue'),
        props: (route) => ({ id: Number(route.params.id) }),
        meta: { title: 'Trang cá nhân' },
      },
      {
        // UC18 — chủ bộ: sửa, chia sẻ, ghi chú (sitemap: auth). Bộ công khai của người khác vẫn đọc được (API cho phép).
        path: 'collections/:id(\\d+)',
        name: 'collection',
        component: () => import('@/pages/CollectionPage.vue'),
        props: (route) => ({ id: Number(route.params.id) }),
        meta: { title: 'Bộ sưu tập', requiresAuth: true },
      },
      {
        // UC18 — link chia sẻ: chỉ bộ công khai, chỉ đọc; về riêng tư thì link chết vĩnh viễn
        path: 'c/:shareSlug',
        name: 'shared-collection',
        component: () => import('@/pages/CollectionPage.vue'),
        props: true,
        meta: { title: 'Bộ sưu tập' },
      },
      {
        // P09 — chưa sở hữu gì → form yêu cầu sở hữu (UC23); `?place=<slug>` chọn sẵn địa điểm (nút trên P03)
        path: 'owner',
        name: 'owner',
        component: () => import('@/pages/owner/OwnerHomePage.vue'),
        meta: { title: 'Địa điểm của tôi', requiresAuth: true },
      },
      {
        // P09 — tab ?tab=stats|info|reviews (UC24–UC26)
        path: 'owner/places/:id(\\d+)',
        name: 'owner-place',
        component: () => import('@/pages/owner/OwnerPlacePage.vue'),
        props: (route) => ({ id: Number(route.params.id) }),
        meta: { title: 'Quản lý địa điểm', anyPermission: [Permission.PLACE_UPDATE_OWN] },
      },
      {
        // P05 — một trang có thanh tiến độ, cảnh báo trùng U7 (sitemap)
        path: 'propose',
        name: 'propose',
        component: () => import('@/pages/ProposePlacePage.vue'),
        meta: { title: 'Đề xuất địa điểm', requiresAuth: true },
      },
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
      {
        // UC34 — ảnh trang chủ (thêm 2026-10-17, mở lại Q4)
        path: 'banners',
        name: 'admin-banners',
        component: () => import('@/pages/admin/AdminBannersPage.vue'),
        meta: { title: 'Ảnh trang chủ', anyPermission: [Permission.BANNER_MANAGE] },
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
  // Đổi bộ lọc trên P02 (cùng route, chỉ khác query) giữ nguyên vị trí cuộn; trang mới thì về đầu trang
  scrollBehavior: (to, from, saved) =>
    saved ?? (to.name === 'search' && from.name === 'search' ? false : { top: 0 }),
})

installGuards(router, () => useAuthStore())

export default router
