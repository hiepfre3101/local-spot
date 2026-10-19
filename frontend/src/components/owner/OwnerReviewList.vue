<script setup lang="ts">
import { useInfiniteQuery, useQueryClient } from '@tanstack/vue-query'
import { computed } from 'vue'

import { placesApi } from '@/api/places'
import LsRating from '@/components/place/LsRating.vue'
import LsAvatar from '@/components/ui/LsAvatar.vue'
import LsButton from '@/components/ui/LsButton.vue'
import type { Review } from '@/types/api'
import { formatDate } from '@/utils/format'

import OwnerReplyBox from './OwnerReplyBox.vue'

/**
 * Đánh giá & phản hồi (UC25): mặc định "Cần phản hồi" (`unreplied=true` — thêm 2026-10-18), hoặc tất cả. Mới nhất
 * trước, keyset. `limit` nhỏ cho cột bên cạnh biểu đồ ở tab Thống kê (`compact`: ẩn bộ lọc + "Xem thêm").
 */
const props = withDefaults(
  defineProps<{ placeId: number; unreplied?: boolean; limit?: number; compact?: boolean }>(),
  { unreplied: true, limit: 10, compact: false },
)

const queryClient = useQueryClient()

const reviews = useInfiniteQuery({
  queryKey: computed(() => ['owner', 'reviews', props.placeId, props.unreplied, props.limit]),
  queryFn: ({ pageParam }) =>
    placesApi.reviews(props.placeId, {
      sort: 'NEWEST',
      unreplied: props.unreplied,
      cursor: pageParam,
      limit: props.limit,
    }),
  initialPageParam: null as string | null,
  getNextPageParam: (last) => last.nextCursor,
})
const items = computed(() => reviews.data.value?.pages.flatMap((p) => p.items) ?? [])

/** Phản hồi xong vẫn giữ mục trên màn (để thấy kết quả); các danh sách khác của địa điểm nạp lại. */
function changed(): void {
  void queryClient.invalidateQueries({
    queryKey: ['owner', 'reviews', props.placeId],
    refetchType: 'inactive',
  })
  void queryClient.invalidateQueries({ queryKey: ['place', props.placeId, 'reviews'] })
}

function excerpt(review: Review): string {
  return props.compact && review.content.length > 160
    ? `${review.content.slice(0, 160).trimEnd()}…`
    : review.content
}
</script>

<template>
  <div :aria-busy="reviews.isFetching.value">
    <p v-if="reviews.isError.value" role="alert">✕ Chưa tải được đánh giá.</p>
    <p v-else-if="reviews.isSuccess.value && !items.length" class="text-ink-muted">
      {{
        unreplied ? '✓ Bạn đã phản hồi mọi đánh giá.' : 'Địa điểm chưa có đánh giá nào được đăng.'
      }}
    </p>
    <ul class="m-0 list-none p-0">
      <li
        v-for="review in items"
        :key="review.id"
        class="flex flex-col gap-3 border-b border-line py-4"
      >
        <div class="flex flex-wrap items-center justify-between gap-2">
          <span class="flex items-center gap-2">
            <LsAvatar
              v-if="!compact"
              :name="review.author.displayName"
              :src="review.author.avatarUrl"
              size="sm"
            />
            <b>{{ review.author.displayName }}</b>
          </span>
          <span class="flex items-center gap-3">
            <LsRating :score="review.rating" />
            <span class="text-caption text-ink-muted">{{ formatDate(review.createdAt) }}</span>
          </span>
        </div>
        <p class="m-0 whitespace-pre-line">“{{ excerpt(review) }}”</p>
        <OwnerReplyBox :review="review" @changed="changed" />
      </li>
    </ul>
    <div v-if="!compact && reviews.hasNextPage.value" class="pt-6">
      <LsButton
        variant="ghost"
        :loading="reviews.isFetchingNextPage.value"
        @click="reviews.fetchNextPage()"
        >Xem thêm đánh giá</LsButton
      >
    </div>
  </div>
</template>
