<script setup lang="ts">
import { useInfiniteQuery } from '@tanstack/vue-query'
import { computed, useId } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { moderationApi } from '@/api/moderation'
import { problemMessage, toProblem } from '@/api/problem'
import LsAdminButton from '@/components/ui/LsAdminButton.vue'
import { useAuthStore } from '@/stores/auth'
import type { ModerationItemType, ModerationQueueItem } from '@/types/api'
import { formatNumber } from '@/utils/format'

import { MODERATION_TYPES, TYPE_LABELS, TYPE_PERMISSIONS } from './moderation'
import QueueTable from './QueueTable.vue'

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
      <QueueTable :rows="rows" :loading="queue.isPending.value" :failed="!!errorText" :now="now" />
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
