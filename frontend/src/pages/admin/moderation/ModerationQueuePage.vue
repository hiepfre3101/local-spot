<script setup lang="ts">
import { useInfiniteQuery } from '@tanstack/vue-query'
import { computed, useId } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { moderationApi } from '@/api/moderation'
import { problemMessage, toProblem } from '@/api/problem'
import LsAdminButton from '@/components/ui/LsAdminButton.vue'
import LsBadge from '@/components/ui/LsBadge.vue'
import { useAuthStore } from '@/stores/auth'
import type { ModerationItemType, ModerationQueueItem } from '@/types/api'
import { formatNumber, formatShortDateTime } from '@/utils/format'

import {
  detailRoute,
  MODERATION_TYPES,
  REPORT_TARGET_LABELS,
  TYPE_LABELS,
  TYPE_PERMISSIONS,
  waitedText,
} from './moderation'

/**
 * Hàng chờ kiểm duyệt gộp (UC27–UC30, DataTable): **FIFO** — cũ nhất trước, không cho đổi sắp xếp; cột # là thứ tự xử
 * lý, "Đã chờ" là tuổi của mục. Chỉ hàng đầu có nút chính "Xử lý tiếp", các hàng sau là "Xem". Lọc loại qua `?type=`
 * (mục sidebar), chỉ những loại người dùng có quyền — kiểm duyệt viên và quản trị viên dùng chung màn này.
 */
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const uid = useId()

const allowedTypes = computed(() => MODERATION_TYPES.filter((t) => auth.can(TYPE_PERMISSIONS[t])))
const type = computed<ModerationItemType | null>(() => {
  const requested = route.query.type
  return typeof requested === 'string' &&
    allowedTypes.value.includes(requested as ModerationItemType)
    ? (requested as ModerationItemType)
    : null
})

function setType(value: string): void {
  void router.replace({ query: value ? { type: value } : {} })
}

const queue = useInfiniteQuery({
  queryKey: computed(() => ['moderation', 'queue', type.value]),
  queryFn: ({ pageParam }) => moderationApi.queue(type.value, pageParam),
  initialPageParam: null as string | null,
  getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  staleTime: 0,
})

const rows = computed<ModerationQueueItem[]>(
  () => queue.data.value?.pages.flatMap((p) => p.items) ?? [],
)
const errorText = computed(() =>
  queue.error.value ? problemMessage(toProblem(queue.error.value)) : null,
)
const now = Date.now()

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
  <div>
    <div class="ls-pagehead">
      <div>
        <h1>{{ type ? `Kiểm duyệt · ${TYPE_LABELS[type]}` : 'Hàng chờ kiểm duyệt' }}</h1>
        <p>Cũ nhất trước (FIFO) — xử lý theo đúng thứ tự gửi.</p>
      </div>
      <div v-if="allowedTypes.length > 1">
        <label class="sr-only" :for="`${uid}-type`">Loại việc</label>
        <span class="ls-selectwrap w-[180px]">
          <select
            :id="`${uid}-type`"
            class="ls-select"
            :value="type ?? ''"
            @change="setType(($event.target as HTMLSelectElement).value)"
          >
            <option value="">Mọi loại việc</option>
            <option v-for="t in allowedTypes" :key="t" :value="t">{{ TYPE_LABELS[t] }}</option>
          </select>
        </span>
      </div>
    </div>

    <section class="ls-section">
      <p v-if="errorText" role="alert" class="ls-field__error">✕ {{ errorText }}</p>
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
            <tr v-if="queue.isPending.value">
              <td colspan="8" class="text-ink-muted">Đang tải…</td>
            </tr>
            <tr v-else-if="!rows.length && !errorText">
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
                <time :datetime="item.submittedAt">{{
                  formatShortDateTime(item.submittedAt)
                }}</time>
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
      <div class="flex items-center justify-between py-4 text-caption text-ink-muted">
        <span>Đang hiển thị {{ formatNumber(rows.length) }} việc</span>
        <LsAdminButton
          v-if="queue.hasNextPage.value"
          size="sm"
          :loading="queue.isFetchingNextPage.value"
          @click="queue.fetchNextPage()"
        >
          Tải thêm
        </LsAdminButton>
      </div>
    </section>
  </div>
</template>
