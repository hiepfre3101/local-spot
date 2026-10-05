import type { RouteLocationNormalized, RouteLocationRaw, Router } from 'vue-router'

import { safeRedirect } from './redirect'

declare module 'vue-router' {
  interface RouteMeta {
    /** Tiêu đề tab trình duyệt. */
    title?: string
    /** Cần đăng nhập — khách bị chuyển tới /login?redirect=… (sitemap §2). */
    requiresAuth?: boolean
    /** Chỉ khách (đăng nhập, đăng ký…) — đã đăng nhập thì về trang trước / trang chủ. */
    guestOnly?: boolean
    /** Cần có ít nhất một permission trong danh sách (`perm:x` của sitemap). */
    anyPermission?: readonly string[]
  }
}

export interface GuardAuth {
  init(): Promise<void>
  readonly isAuthenticated: boolean
  canAny(permissions: readonly string[]): boolean
}

/**
 * Quyết định điều hướng — hàm thuần để test được không cần trình duyệt. Quyền xét theo **mọi** route khớp (route cha
 * `/admin` yêu cầu quyền nhân sự, route con yêu cầu quyền riêng).
 */
export async function resolveNavigation(
  to: Pick<RouteLocationNormalized, 'meta' | 'matched' | 'query' | 'fullPath'>,
  auth: GuardAuth,
): Promise<RouteLocationRaw | true> {
  await auth.init()

  if (to.meta.guestOnly && auth.isAuthenticated) {
    return safeRedirect(to.query.redirect)
  }
  const requiresAuth = to.matched.some((r) => r.meta.requiresAuth || r.meta.anyPermission)
  if (requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  const lacksPermission = to.matched.some(
    (r) => r.meta.anyPermission !== undefined && !auth.canAny(r.meta.anyPermission),
  )
  if (lacksPermission) {
    return { name: 'forbidden' }
  }
  return true
}

export function installGuards(router: Router, getAuth: () => GuardAuth): void {
  router.beforeEach((to) => resolveNavigation(to, getAuth()))
  router.afterEach((to) => {
    document.title = to.meta.title ? `${to.meta.title} · LocalSpot` : 'LocalSpot'
  })
}
