import type { Router } from 'vue-router'

export interface PublicNavItem {
  label: string
  routeName: string
  query?: Record<string, string>
}

/** Thanh điều hướng công khai theo sitemap §1 (DefaultLayout). Chỉ hiện mục đã có route — module làm xong tự hiện. */
export const PUBLIC_NAV: readonly PublicNavItem[] = [
  { label: 'Khám phá', routeName: 'search' },
  { label: 'Bản đồ', routeName: 'search', query: { near: 'me' } },
  { label: 'Bảng xếp hạng', routeName: 'leaderboard' },
  { label: 'Đề xuất địa điểm', routeName: 'propose' },
]

export function visiblePublicNav(router: Router): PublicNavItem[] {
  return PUBLIC_NAV.filter((item) => router.hasRoute(item.routeName))
}
