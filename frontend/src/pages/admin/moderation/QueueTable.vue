<script setup lang="ts">
import { RouterLink } from 'vue-router'

import LsBadge from '@/components/ui/LsBadge.vue'
import type { ModerationItemType, ModerationQueueItem } from '@/types/api'
import { formatNumber, formatShortDateTime } from '@/utils/format'

import { detailRoute, REPORT_TARGET_LABELS, TYPE_LABELS, waitedText } from './moderation'

/**
 * Bảng hàng chờ kiểm duyệt (DataTable, FIFO): cột # là thứ tự xử lý, "Đã chờ" là tuổi của mục; chỉ hàng đầu có nút chính
 * "Xử lý tiếp", các hàng sau là "Xem". Dùng ở màn Hàng chờ và khối hàng chờ của AdminDashboard (vài mục đầu).
 */
defineProps<{ rows: ModerationQueueItem[]; loading: boolean; failed: boolean; now: number }>()

const STATUS: Record<ModerationItemType, { tone: 'warning' | 'danger' | 'info'; text: string }> = {
  PLACE: { tone: 'warning', text: '● Chờ duyệt' },
  REVIEW: { tone: 'warning', text: '● Chờ duyệt' },
  REPORT: { tone: 'danger', text: '⚑ Bị báo cáo' },
  CLAIM: { tone: 'info', text: '● Chờ xác nhận' },
}

/** Dòng phụ dưới tên: vì sao mục ở đây. */
function subline(item: ModerationQueueItem): string {
  switch (item.type) {
    case 'PLACE':
      return 'Đề xuất địa điểm mới'
    case 'REVIEW': {
      const reasons = item.flags.map((f) =>
        f === 'IP_FLAGGED' ? '⚑ Cùng dải IP' : '▼ Điểm tin cậy thấp',
      )
      return reasons.length ? reasons.join(' · ') : 'Đánh giá chờ duyệt'
    }
    case 'REPORT':
      return `${REPORT_TARGET_LABELS[item.reportTargetType ?? 'REVIEW']} · ${formatNumber(item.reportCount ?? 0)} báo cáo`
    case 'CLAIM':
      return 'Yêu cầu xác nhận sở hữu'
  }
}
</script>

<template>
  <div class="overflow-x-auto">
    <table class="ls-table">
      <thead>
        <tr>
          <th scope="col" class="w-9">#</th>
          <th scope="col" class="w-[90px]">Loại</th>
          <th scope="col">Nội dung</th>
          <th scope="col">Người gửi</th>
          <th scope="col">Gửi lúc ▲</th>
          <th scope="col">Đã chờ</th>
          <th scope="col">Trạng thái</th>
          <th scope="col" class="r">Thao tác</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading">
          <td colspan="8" class="text-ink-muted">Đang tải…</td>
        </tr>
        <tr v-else-if="!rows.length && !failed">
          <td colspan="8" class="text-ink-muted">✓ Không còn việc nào trong hàng chờ.</td>
        </tr>
        <tr v-for="(item, i) in rows" :key="`${item.type}-${item.reportTargetType}-${item.id}`">
          <td class="num text-ink-muted">{{ item.position }}</td>
          <td class="whitespace-nowrap">{{ TYPE_LABELS[item.type] }}</td>
          <td class="min-w-[220px]">
            <RouterLink class="ls-link" :to="detailRoute(item)">{{
              item.title ?? '(Nội dung đã bị xoá)'
            }}</RouterLink>
            <div class="text-caption text-ink-muted">{{ subline(item) }}</div>
          </td>
          <td class="whitespace-nowrap">{{ item.submittedBy ?? 'Tài khoản đã xoá' }}</td>
          <td class="num whitespace-nowrap">
            <time :datetime="item.submittedAt">{{ formatShortDateTime(item.submittedAt) }}</time>
          </td>
          <td class="num whitespace-nowrap">{{ waitedText(item.submittedAt, now) }}</td>
          <td>
            <LsBadge :tone="STATUS[item.type].tone">{{ STATUS[item.type].text }}</LsBadge>
          </td>
          <td class="r">
            <RouterLink
              :class="[
                'ls-abtn ls-abtn--sm whitespace-nowrap',
                i === 0 ? 'ls-abtn--primary' : 'ls-abtn--secondary',
              ]"
              :to="detailRoute(item)"
              :aria-label="`${i === 0 ? 'Xử lý tiếp' : 'Xem'}: ${item.title ?? TYPE_LABELS[item.type]}`"
            >
              {{ i === 0 ? 'Xử lý tiếp' : 'Xem' }}
            </RouterLink>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
