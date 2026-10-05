import type { Router } from 'vue-router'

import { Permission } from '@/auth/permissions'

export interface AdminNavItem {
  label: string
  routeName: string
  query?: Record<string, string>
  /** Có một trong các quyền này thì thấy mục. */
  permissions: readonly string[]
  group: 'Kiểm duyệt' | 'Quản trị' | null
  /** Path SVG nét 1.6px (icon tạm của design system — chưa có bộ icon chính thức). */
  icon: string
}

/**
 * Sidebar quản trị theo vai trò (AdminDashboard README). Mục chỉ hiện khi route đã có trong router **và** người dùng
 * có quyền — module nào làm xong (thêm route) thì mục tự hiện, không phải sửa sidebar.
 */
export const ADMIN_NAV: readonly AdminNavItem[] = [
  {
    label: 'Tổng quan',
    routeName: 'admin-dashboard',
    permissions: [Permission.DASHBOARD_VIEW],
    group: null,
    icon: 'M3 11l9-7 9 7v9H3z',
  },
  // Bốn mục kiểm duyệt cùng là hàng chờ gộp FIFO, lọc theo loại (sitemap §2.3: /admin/queue?type=…)
  {
    label: 'Địa điểm',
    routeName: 'admin-queue',
    query: { type: 'PLACE' },
    permissions: [Permission.PLACE_APPROVE],
    group: 'Kiểm duyệt',
    icon: 'M12 21s-7-6.2-7-11a7 7 0 0114 0c0 4.8-7 11-7 11zM12 12.5a2.5 2.5 0 100-5 2.5 2.5 0 000 5z',
  },
  {
    label: 'Đánh giá',
    routeName: 'admin-queue',
    query: { type: 'REVIEW' },
    permissions: [Permission.REVIEW_MODERATE],
    group: 'Kiểm duyệt',
    icon: 'M12 19a7 7 0 100-14 7 7 0 000 14z',
  },
  {
    label: 'Báo cáo vi phạm',
    routeName: 'admin-queue',
    query: { type: 'REPORT' },
    permissions: [Permission.REPORT_HANDLE],
    group: 'Kiểm duyệt',
    icon: 'M5 21V4h11l-2 4 2 4H5',
  },
  {
    label: 'Yêu cầu sở hữu',
    routeName: 'admin-queue',
    query: { type: 'CLAIM' },
    permissions: [Permission.CLAIM_APPROVE],
    group: 'Kiểm duyệt',
    icon: 'M8 19a4 4 0 100-8 4 4 0 000 8zM11 12l9-9M16 7l3 3',
  },
  {
    label: 'Người dùng & vai trò',
    routeName: 'admin-users',
    permissions: [Permission.USER_VIEW],
    group: 'Quản trị',
    icon: 'M12 12a4 4 0 100-8 4 4 0 000 8zM4 21c1-4 4-6 8-6s7 2 8 6',
  },
  {
    label: 'Danh mục & tiện ích',
    routeName: 'admin-catalog',
    permissions: [Permission.CATEGORY_MANAGE, Permission.AMENITY_MANAGE],
    group: 'Quản trị',
    icon: 'M3 3h8l10 10-8 8L3 11z',
  },
]

export function visibleAdminNav(
  router: Router,
  canAny: (permissions: readonly string[]) => boolean,
) {
  return ADMIN_NAV.filter((item) => router.hasRoute(item.routeName) && canAny(item.permissions))
}
