<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { interactionsApi } from '@/api/interactions'
import { problemMessage, toProblem } from '@/api/problem'
import LsAvatar from '@/components/ui/LsAvatar.vue'
import { useRequireLogin } from '@/composables/useRequireLogin'
import { useNotify } from '@/plugins/toast'
import { useAuthStore } from '@/stores/auth'
import type { Review } from '@/types/api'
import { formatDate, formatNumber } from '@/utils/format'

import CommentThread from './CommentThread.vue'
import LsRating from './LsRating.vue'
import PlacePhoto from './PlacePhoto.vue'
import type { ReportTarget } from './reportTarget'

/**
 * ReviewItem (README thành phần): tác giả, điểm, ngày, "✓ Đã check-in" (UC16), nội dung, ảnh, hàng thao tác, phản hồi
 * của chủ địa điểm (UC25). Các mục xếp chồng, phân tách bằng `line`.
 *
 * Thao tác (`.ls-act`): **Hữu ích** (UC14, `aria-pressed`), **Bình luận** (UC15 — mở luồng bình luận ngay dưới),
 * **Báo cáo** (UC17 — trang cha mở hộp thoại). Đánh giá của chính người xem: **Sửa** (P04 — xoá cũng ở đó) thay cho
 * Hữu ích / Báo cáo. Bản chờ duyệt / bị từ chối của chính mình (trang cha nạp riêng — danh sách công khai chỉ có
 * PUBLISHED) hiện ở đầu danh sách, nền chìm, chỉ có Sửa. Khách thấy đủ nội dung và đọc được bình luận; bấm thao tác ghi → đăng nhập. Không hiện
 * số 0 ("Hữu ích", không phải "Hữu ích · 0").
 */
const props = defineProps<{ review: Review; placeSlug?: string }>()
const emit = defineEmits<{ report: [target: ReportTarget] }>()

const auth = useAuthStore()
const router = useRouter()
const notify = useNotify()
const requireLogin = useRequireLogin()

const helpfulCount = ref(props.review.helpfulCount)
const votedByMe = ref(props.review.votedByMe)
const commentCount = ref(props.review.commentCount)
const showComments = ref(false)
const voting = ref(false)

watch(
  () => props.review,
  (r) => {
    helpfulCount.value = r.helpfulCount
    votedByMe.value = r.votedByMe
    commentCount.value = r.commentCount
  },
)

const mine = computed(() => auth.user?.id === props.review.author.id)
const notPublished = computed(() => props.review.status !== 'PUBLISHED')
const editTarget = computed(() =>
  props.placeSlug && router.hasRoute('write-review')
    ? { name: 'write-review', params: { slug: props.placeSlug } }
    : null,
)

/** Ảnh đang xử lý / lỗi chỉ tác giả thấy (openapi) — chỉ hiện ảnh đã sẵn sàng. */
const photos = computed(() => props.review.photos.filter((p) => p.status === 'READY' && p.largeUrl))

async function toggleVote(): Promise<void> {
  if (!requireLogin() || voting.value) return
  voting.value = true
  try {
    const result = votedByMe.value
      ? await interactionsApi.unvote(props.review.id)
      : await interactionsApi.vote(props.review.id)
    helpfulCount.value = result.helpfulCount
    votedByMe.value = result.votedByMe
  } catch (cause) {
    notify.error(problemMessage(toProblem(cause)))
  } finally {
    voting.value = false
  }
}

function report(): void {
  if (!requireLogin()) return
  emit('report', {
    type: 'REVIEW',
    id: props.review.id,
    label: `đánh giá của ${props.review.author.displayName}`,
  })
}
</script>

<template>
  <article :class="['ls-ritem', notPublished && 'rounded-md bg-surface-sunk px-4']">
    <LsAvatar :name="review.author.displayName" :src="review.author.avatarUrl" />
    <div class="min-w-0">
      <div class="ls-ritem__head">
        <span class="ls-ritem__name">{{ review.author.displayName }}</span>
      </div>
      <p v-if="review.status === 'PENDING'" class="m-0 mt-1 text-caption font-semibold">
        ● Đang chờ duyệt — chỉ bạn thấy
      </p>
      <p v-else-if="review.status === 'REJECTED'" class="m-0 mt-1 text-caption">
        <b>✕ Bị từ chối:</b> {{ review.rejectReason }}
      </p>
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

      <div class="ls-actions">
        <template v-if="!notPublished">
          <button
            v-if="!mine"
            type="button"
            class="ls-act"
            :aria-pressed="votedByMe"
            :disabled="voting"
            @click="toggleVote"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M7 11v9H4v-9zM7 11l4-8c1.7 0 3 1.3 3 3v3h5a2 2 0 0 1 2 2.3l-1.2 7A2 2 0 0 1 17.8 20H7"
              />
            </svg>
            Hữu ích<template v-if="helpfulCount > 0"> · {{ formatNumber(helpfulCount) }}</template>
          </button>
          <span v-else-if="helpfulCount > 0" class="ls-act">
            Hữu ích · {{ formatNumber(helpfulCount) }}
          </span>
          <button
            type="button"
            class="ls-act"
            :aria-expanded="showComments"
            @click="showComments = !showComments"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 5h16v11H9l-5 4z" />
            </svg>
            Bình luận<template v-if="commentCount > 0">
              · {{ formatNumber(commentCount) }}</template
            >
          </button>
          <button v-if="!mine" type="button" class="ls-act" @click="report">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M5 21V4h11l-2 4 2 4H5" />
            </svg>
            Báo cáo
          </button>
        </template>
        <RouterLink v-if="mine && editTarget" class="ls-act" :to="editTarget">Sửa</RouterLink>
      </div>

      <CommentThread
        v-if="showComments"
        :review-id="review.id"
        @report="emit('report', $event)"
        @count-change="commentCount = Math.max(0, commentCount + $event)"
      />

      <div v-if="review.ownerReply" class="ls-reply">
        <b>Phản hồi từ chủ địa điểm · {{ formatDate(review.ownerReply.createdAt) }}</b>
        {{ review.ownerReply.content }}
      </div>
    </div>
  </article>
</template>
