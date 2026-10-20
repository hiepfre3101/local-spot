<script setup lang="ts">
import { computed } from 'vue'

import { formatNumber } from '@/utils/format'
import { discFills, ratingLabel, scoreText } from '@/utils/rating'

/**
 * Rating (README thành phần): 5 hình tròn tô `sun` theo tỷ lệ điểm + số điểm + số lượt đánh giá. Cả cụm là `role="img"`
 * với nhãn đọc được ("4,7 trên 5 điểm, 2.314 đánh giá") — không dựa vào hình tròn để truyền điểm. Chưa có đánh giá
 * (`count = 0`) thì nói thẳng bằng chữ thay vì 5 hình trống.
 */
const props = withDefaults(defineProps<{ score: number; count?: number; size?: 'md' | 'lg' }>(), {
  count: undefined,
  size: 'md',
})

const unrated = computed(() => props.count === 0)
const fills = computed(() => discFills(props.score))
</script>

<template>
  <span v-if="unrated" class="ls-rating font-normal text-ink-muted">Chưa có đánh giá</span>
  <span v-else class="ls-rating" role="img" :aria-label="ratingLabel(score, count)">
    <span :class="['ls-stars', size === 'lg' && 'ls-stars--lg']" aria-hidden="true">
      <span v-for="(fill, i) in fills" :key="i" class="ls-star"
        ><i :style="{ width: `${fill}%` }"></i
      ></span>
    </span>
    <b>{{ scoreText(score) }}</b>
    <span v-if="count !== undefined" class="ls-rating__n">({{ formatNumber(count) }})</span>
  </span>
</template>
