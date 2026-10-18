<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import LsRating from '@/components/place/LsRating.vue'
import PlacePhoto from '@/components/place/PlacePhoto.vue'
import type { Review } from '@/types/api'
import { formatDate, formatNumber } from '@/utils/format'

/**
 * Một đánh giá trên trang cá nhân (P06): tên địa điểm (liên kết) thay cho tác giả — trang đã là của tác giả. Trạng
 * thái chỉ hiện khi khác "đã đăng" (trang của chính mình — `/me/reviews` có cả chờ duyệt / bị từ chối / bị ẩn), luôn có
 * ký hiệu + chữ. Thao tác (vote, bình luận) ở trang địa điểm.
 */
const props = defineProps<{ review: Review }>()

const STATUS: Record<string, string> = {
  PENDING: '● Đang chờ duyệt — chỉ bạn thấy',
  REJECTED: '✕ Bị từ chối',
  HIDDEN: '✕ Đã bị ẩn sau báo cáo',
}
const status = computed(() => STATUS[props.review.status] ?? null)
const photos = computed(() =>
  props.review.photos.filter((p) => p.status === 'READY' && p.thumbUrl).slice(0, 4),
)
</script>

<template>
  <article class="border-b border-line py-5">
    <RouterLink
      class="text-title font-semibold text-ink"
      :to="{ name: 'place', params: { slug: review.place.slug } }"
      >{{ review.place.name }}</RouterLink
    >
    <div class="mt-1 flex flex-wrap items-center gap-3 text-caption text-ink-muted">
      <LsRating :score="review.rating" />
      <time :datetime="review.createdAt">{{ formatDate(review.createdAt) }}</time>
      <span v-if="review.helpfulCount > 0">Hữu ích · {{ formatNumber(review.helpfulCount) }}</span>
    </div>
    <p v-if="status" class="mt-1 mb-0 text-caption font-semibold">
      {{ status
      }}<template v-if="review.status === 'REJECTED' && review.rejectReason"
        >: {{ review.rejectReason }}</template
      >
    </p>
    <p class="mt-2 mb-0 break-words whitespace-pre-line">{{ review.content }}</p>
    <div v-if="photos.length" class="mt-3 flex gap-2">
      <PlacePhoto
        v-for="photo in photos"
        :key="photo.id"
        :photo="photo"
        sizes="80px"
        class="h-20 w-20 overflow-hidden rounded-md"
      />
    </div>
  </article>
</template>
