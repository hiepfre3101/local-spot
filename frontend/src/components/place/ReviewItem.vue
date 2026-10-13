<script setup lang="ts">
import { computed } from 'vue'

import LsAvatar from '@/components/ui/LsAvatar.vue'
import type { Review } from '@/types/api'
import { formatDate, formatNumber } from '@/utils/format'

import LsRating from './LsRating.vue'
import PlacePhoto from './PlacePhoto.vue'

/**
 * ReviewItem (README thành phần) — bản chỉ đọc của đợt khám phá: tác giả, điểm, ngày, "✓ Đã check-in" (UC16), nội dung,
 * ảnh, số lượt hữu ích / bình luận, phản hồi của chủ địa điểm (UC25). Các mục xếp chồng, phân tách bằng `line`.
 * Nút Hữu ích / Bình luận / Báo cáo / Sửa / Xoá (UC13–UC15, UC17) thêm ở đợt tương tác — không hiện số 0.
 */
const props = defineProps<{ review: Review }>()

/** Ảnh đang xử lý / lỗi chỉ tác giả thấy (openapi) — bản chỉ đọc chỉ hiện ảnh đã sẵn sàng. */
const photos = computed(() => props.review.photos.filter((p) => p.status === 'READY' && p.largeUrl))
</script>

<template>
  <article class="ls-ritem">
    <LsAvatar :name="review.author.displayName" :src="review.author.avatarUrl" />
    <div class="min-w-0">
      <div class="ls-ritem__head">
        <span class="ls-ritem__name">{{ review.author.displayName }}</span>
      </div>
      <div class="ls-ritem__meta mt-1 flex flex-wrap items-center gap-3">
        <LsRating :score="review.rating" />
        <time :datetime="review.createdAt">{{ formatDate(review.createdAt) }}</time>
        <span v-if="review.authorCheckedIn">✓ Đã check-in</span>
      </div>
      <p class="ls-ritem__body">{{ review.content }}</p>
      <div v-if="photos.length" class="ls-ritem__pics">
        <a
          v-for="(photo, i) in photos"
          :key="photo.id"
          :href="photo.largeUrl!"
          target="_blank"
          rel="noopener"
          :aria-label="`Ảnh ${i + 1} của đánh giá, mở ảnh lớn`"
        >
          <PlacePhoto :photo="photo" sizes="96px" />
        </a>
      </div>
      <div v-if="review.helpfulCount > 0 || review.commentCount > 0" class="ls-actions">
        <span v-if="review.helpfulCount > 0" class="ls-act">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M7 11v9H4v-9zM7 11l4-8c1.7 0 3 1.3 3 3v3h5a2 2 0 0 1 2 2.3l-1.2 7A2 2 0 0 1 17.8 20H7"
            />
          </svg>
          Hữu ích · {{ formatNumber(review.helpfulCount) }}
        </span>
        <span v-if="review.commentCount > 0" class="ls-act">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 5h16v11H9l-5 4z" />
          </svg>
          Bình luận · {{ formatNumber(review.commentCount) }}
        </span>
      </div>
      <div v-if="review.ownerReply" class="ls-reply">
        <b>Phản hồi từ chủ địa điểm · {{ formatDate(review.ownerReply.createdAt) }}</b>
        {{ review.ownerReply.content }}
      </div>
    </div>
  </article>
</template>
