<script setup lang="ts">
import { RouterLink } from 'vue-router'

import type { HomeReview } from '@/types/api'
import { formatNumber } from '@/utils/format'

import LsRating from './LsRating.vue'

/** ReviewCard (README thành phần): trích một đánh giá nổi bật trên trang chủ, nền `sun`, tối đa một thẻ mỗi hàng. */
defineProps<{ review: HomeReview }>()
</script>

<template>
  <article class="ls-review">
    <blockquote>“{{ review.excerpt }}”</blockquote>
    <footer>
      <span>
        <b>{{ review.author.displayName }}</b> ·
        <RouterLink :to="{ name: 'place', params: { slug: review.place.slug } }">{{
          review.place.name
        }}</RouterLink>
        <template v-if="review.helpfulCount > 0">
          <br />{{ formatNumber(review.helpfulCount) }} thấy hữu ích
        </template>
      </span>
      <LsRating :score="review.rating" />
    </footer>
  </article>
</template>
