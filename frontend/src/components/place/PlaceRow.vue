<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import type { PlaceSummary } from '@/types/api'
import { formatPriceRange, tagLabel } from '@/utils/format'
import { formatDistance } from '@/utils/geo'

import LsRating from './LsRating.vue'
import PlacePhoto from './PlacePhoto.vue'

/**
 * Hàng kết quả gọn của P02: ảnh 132px, tên, Rating, danh mục · khoảng cách, thẻ. `active` = đang chọn cùng ghim trên bản
 * đồ (tên gạch chân — trạng thái không chỉ bằng màu). Rê chuột / focus báo lên để bản đồ đánh dấu ghim tương ứng.
 */
const props = defineProps<{ place: PlaceSummary; active: boolean }>()
defineEmits<{ hover: [placeId: number | null] }>()

const meta = computed(() =>
  [
    props.place.category.name,
    props.place.distanceM !== null ? formatDistance(props.place.distanceM) : null,
    formatPriceRange(props.place.priceMin, props.place.priceMax),
  ]
    .filter(Boolean)
    .join(' · '),
)
const tags = computed(() => props.place.tags.slice(0, 3))
</script>

<template>
  <article
    class="ls-rrow"
    :aria-current="active || undefined"
    @mouseenter="$emit('hover', place.id)"
    @mouseleave="$emit('hover', null)"
    @focusin="$emit('hover', place.id)"
    @focusout="$emit('hover', null)"
  >
    <PlacePhoto :photo="place.coverPhoto" sizes="132px" />
    <div class="min-w-0">
      <h3>
        <RouterLink :to="{ name: 'place', params: { slug: place.slug } }">{{
          place.name
        }}</RouterLink>
      </h3>
      <LsRating :score="place.bayesianScore" :count="place.reviewCount" />
      <p class="ls-spot__meta mt-1.5">{{ meta }}</p>
      <ul v-if="tags.length" class="ls-tags mt-1.5" aria-label="Thẻ">
        <li v-for="tag in tags" :key="tag">{{ tagLabel(tag) }}</li>
      </ul>
    </div>
  </article>
</template>
