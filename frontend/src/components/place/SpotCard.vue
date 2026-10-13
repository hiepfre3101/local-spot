<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import type { PlaceSummary } from '@/types/api'
import { tagLabel } from '@/utils/format'
import { formatDistance } from '@/utils/geo'

import LsRating from './LsRating.vue'
import PlacePhoto from './PlacePhoto.vue'

/**
 * SpotCard (README thành phần): ảnh 4:3, tên, Rating, dòng meta, tối đa 3 thẻ `#tag` — nằm thẳng trên `surface`, không
 * khung / bóng. Cả thẻ bấm được qua liên kết ở tên. Nút lưu (UC18) thêm khi module bộ sưu tập có API.
 */
const props = withDefaults(
  defineProps<{ place: PlaceSummary; featured?: boolean; headingLevel?: 'h2' | 'h3' }>(),
  { featured: false, headingLevel: 'h3' },
)

const meta = computed(() =>
  [
    props.place.category.name,
    props.place.distanceM !== null ? `cách ${formatDistance(props.place.distanceM)}` : null,
  ]
    .filter(Boolean)
    .join(' · '),
)
const tags = computed(() => props.place.tags.slice(0, 3))
</script>

<template>
  <article class="ls-spot">
    <PlacePhoto
      :photo="place.coverPhoto"
      sizes="(min-width: 1024px) 280px, (min-width: 768px) 45vw, 90vw"
    >
      <span v-if="featured" class="ls-chip ls-chip--sun">Nổi bật</span>
    </PlacePhoto>
    <component :is="headingLevel" class="ls-spot__name">
      <RouterLink :to="{ name: 'place', params: { slug: place.slug } }">{{
        place.name
      }}</RouterLink>
    </component>
    <LsRating :score="place.bayesianScore" :count="place.reviewCount" />
    <p class="ls-spot__meta">{{ meta }}</p>
    <ul v-if="tags.length" class="ls-tags" aria-label="Thẻ">
      <li v-for="tag in tags" :key="tag">{{ tagLabel(tag) }}</li>
    </ul>
  </article>
</template>
