<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, useTemplateRef } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { moderationApi } from '@/api/moderation'
import { problemMessage, toProblem } from '@/api/problem'
import PlacePhoto from '@/components/place/PlacePhoto.vue'
import LsAdminButton from '@/components/ui/LsAdminButton.vue'
import LsBadge from '@/components/ui/LsBadge.vue'
import { useAuthStore } from '@/stores/auth'
import type { ReviewStatus } from '@/types/api'
import { formatDate, formatDateTime, formatNumber } from '@/utils/format'

import ModerationHead from './ModerationHead.vue'
import RejectReason from './RejectReason.vue'
import { useDecision } from './useDecision'

/**
 * Duyệt đánh giá trong hàng chờ (UC28): nội dung gốc · tác giả (điểm tin cậy, vi phạm còn hiệu lực, lịch sử review) ·
 * dấu hiệu nghi ngờ (cờ IP, review khác cùng dải IP trong 24 giờ — U3). Từ chối ghi **một vi phạm** cho tác giả
 * (requirements §5.1) nên màn nói rõ trước khi bấm. Không tự duyệt review của mình hay về địa điểm mình sở hữu.
 */
const route = useRoute()
const auth = useAuthStore()
const reviewId = computed(() => Number(route.params.id))

const detail = useQuery({
  queryKey: computed(() => ['moderation', 'review', reviewId.value]),
  queryFn: () => moderationApi.review(reviewId.value),
  staleTime: 0,
})
const data = computed(() => detail.data.value ?? null)
const item = computed(() => data.value?.item ?? null)
const review = computed(() => item.value?.review ?? null)
const loadError = computed(() =>
  detail.error.value ? problemMessage(toProblem(detail.error.value)) : null,
)

const STATUS: Record<
  ReviewStatus,
  { tone: 'warning' | 'success' | 'danger' | 'neutral'; text: string }
> = {
  PENDING: { tone: 'warning', text: '● Chờ duyệt' },
  PUBLISHED: { tone: 'success', text: '✓ Đã đăng' },
  REJECTED: { tone: 'danger', text: '✕ Đã từ chối' },
  HIDDEN: { tone: 'neutral', text: '✕ Đã ẩn' },
}
const COUNT_LABELS: Record<ReviewStatus, string> = {
  PUBLISHED: 'Đã đăng',
  PENDING: 'Chờ duyệt',
  REJECTED: 'Bị từ chối',
  HIDDEN: 'Bị ẩn',
}

const pending = computed(() => review.value?.status === 'PENDING')
const selfAction = computed(() => {
  if (!item.value || !auth.user) return false
  return (
    item.value.review.author.id === auth.user.id ||
    auth.user.ownedPlaceIds.includes(item.value.place.id)
  )
})
const photos = computed(() => review.value?.photos ?? [])
const countsText = computed(() => {
  const counts = data.value?.authorReviewCounts
  if (!counts) return ''
  return (Object.keys(COUNT_LABELS) as ReviewStatus[])
    .map((s) => `${COUNT_LABELS[s]} ${formatNumber(counts[s] ?? 0)}`)
    .join(' · ')
})

const reason = ref('')
const reasonError = ref<string | null>(null)
const reasonSection = useTemplateRef<HTMLElement>('reasonSection')
const { submitting, error, submit } = useDecision('REVIEW', () => detail.refetch())

async function approve(): Promise<void> {
  if (!review.value) return
  const version = review.value.version
  await submit(
    () => moderationApi.decideReview(reviewId.value, { decision: 'APPROVE', version }),
    'Đã duyệt và đăng đánh giá.',
  )
}

async function reject(): Promise<void> {
  if (!review.value) return
  if (!reason.value.trim()) {
    reasonError.value = 'Chọn lý do (hoặc ghi rõ lý do khác) trước khi từ chối.'
    reasonSection.value?.querySelector('select')?.focus()
    return
  }
  reasonError.value = null
  const version = review.value.version
  await submit(
    () =>
      moderationApi.decideReview(reviewId.value, {
        decision: 'REJECT',
        reason: reason.value.trim(),
        version,
      }),
    'Đã từ chối đánh giá.',
  )
}
</script>

<template>
  <div>
    <p v-if="detail.isPending.value" class="text-ink-muted">Đang tải…</p>
    <p v-else-if="loadError" role="alert" class="ls-field__error">✕ {{ loadError }}</p>

    <template v-else-if="data && item && review">
      <ModerationHead type="REVIEW" :ref-label="`#${review.id}`" :title="item.place.name">
        <template #meta>
          <LsBadge :tone="STATUS[review.status].tone">{{ STATUS[review.status].text }}</LsBadge>
          <span
            >Viết bởi <b>{{ review.author.displayName }}</b></span
          >
          <time :datetime="review.createdAt">{{ formatDateTime(review.createdAt) }}</time>
          <span
            >Điểm <b>{{ review.rating }}/5</b></span
          >
        </template>
        <template v-if="pending" #actions>
          <LsAdminButton
            variant="danger"
            :disabled="selfAction"
            :loading="submitting"
            @click="reject"
          >
            Từ chối
          </LsAdminButton>
          <LsAdminButton
            variant="primary"
            :disabled="selfAction"
            :loading="submitting"
            @click="approve"
          >
            Duyệt &amp; đăng
          </LsAdminButton>
        </template>
      </ModerationHead>

      <p v-if="error" role="alert" class="ls-field__error mt-4">✕ {{ error }}</p>
      <p v-if="pending && selfAction" class="mt-4 text-caption text-ink-muted">
        ● Đánh giá của chính bạn hoặc về địa điểm bạn sở hữu — một kiểm duyệt viên khác sẽ duyệt.
      </p>
      <p v-if="!pending" class="mt-4 mb-0">
        Đánh giá này đã được xử lý<template v-if="review.rejectReason">
          — lý do từ chối: <b>{{ review.rejectReason }}</b></template
        >.
      </p>

      <section class="ls-section fs">
        <div class="fs__intro">
          <h2>Nội dung đánh giá</h2>
          <p>
            Tại
            <RouterLink
              class="ls-link"
              :to="{ name: 'place', params: { slug: item.place.slug } }"
              >{{ item.place.name }}</RouterLink
            >
            · {{ item.place.address }}
          </p>
        </div>
        <div class="grid max-w-[720px] gap-6">
          <dl class="ls-dl fs__grid">
            <div>
              <dt>Điểm</dt>
              <dd>{{ review.rating }}/5</dd>
            </div>
            <div>
              <dt>Ngày ghé</dt>
              <dd>{{ formatDate(review.visitedAt) }}</dd>
            </div>
            <div class="col-span-full">
              <dt>Nội dung</dt>
              <dd>{{ review.content }}</dd>
            </div>
          </dl>
          <ul v-if="photos.length" class="ls-thumbs m-0 list-none p-0" aria-label="Ảnh đính kèm">
            <li v-for="(photo, i) in photos" :key="photo.id" class="ls-thumb">
              <PlacePhoto :photo="photo" :alt="`Ảnh ${i + 1} của đánh giá`" sizes="180px" />
            </li>
          </ul>
        </div>
      </section>

      <section class="ls-section fs">
        <div class="fs__intro">
          <h2>Tác giả</h2>
          <p>
            <RouterLink
              class="ls-link"
              :to="{ name: 'profile', params: { id: review.author.id } }"
              >{{ review.author.displayName }}</RouterLink
            >
            · tham gia {{ formatDate(data.authorJoinedAt) }}
          </p>
        </div>
        <dl class="ls-dl fs__grid">
          <div>
            <dt>Điểm tin cậy</dt>
            <dd>{{ formatNumber(item.authorTrustScore) }}</dd>
          </div>
          <div>
            <dt>Vi phạm còn hiệu lực</dt>
            <dd>
              {{
                item.authorActiveViolations > 0 ? `▲ ${item.authorActiveViolations}` : 'Không có'
              }}
            </dd>
          </div>
          <div class="col-span-full">
            <dt>Đánh giá đã viết</dt>
            <dd>{{ countsText }}</dd>
          </div>
        </dl>
      </section>

      <section class="ls-section fs">
        <div class="fs__intro">
          <h2>Dấu hiệu nghi ngờ</h2>
          <p>Đánh giá khác về cùng địa điểm từ cùng dải IP, trong 24 giờ trước hoặc sau.</p>
        </div>
        <div class="grid max-w-[720px] gap-4">
          <p class="m-0">
            <LsBadge v-if="item.ipFlagged" tone="danger">⚑ Bị gắn cờ IP</LsBadge>
            <LsBadge v-else tone="neutral">✓ Không có cờ IP</LsBadge>
            <span class="ml-3 text-caption text-ink-muted"
              >{{ formatNumber(item.sameSubnetReviewCount24h) }} đánh giá cùng dải IP (tính cả đã
              xoá)</span
            >
          </p>
          <table v-if="data.sameSubnetReviews.length" class="ls-table">
            <thead>
              <tr>
                <th scope="col">Tác giả</th>
                <th scope="col">Nội dung</th>
                <th scope="col">Trạng thái</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="other in data.sameSubnetReviews" :key="other.id">
                <td class="whitespace-nowrap">{{ other.author.displayName }}</td>
                <td>
                  <RouterLink
                    v-if="other.status === 'PENDING'"
                    class="ls-link"
                    :to="{ name: 'admin-moderation-review', params: { id: other.id } }"
                    >{{ other.rating }}/5 · {{ other.content.slice(0, 80) }}</RouterLink
                  >
                  <template v-else
                    >{{ other.rating }}/5 · {{ other.content.slice(0, 80) }}</template
                  >
                  <div class="text-caption text-ink-muted">
                    {{ formatDateTime(other.createdAt) }}
                  </div>
                </td>
                <td>
                  <LsBadge :tone="STATUS[other.status].tone">{{
                    STATUS[other.status].text
                  }}</LsBadge>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <div v-if="pending && !selfAction" ref="reasonSection">
        <RejectReason
          v-model="reason"
          :presets="[
            'Quảng cáo hoặc spam',
            'Không liên quan đến địa điểm',
            'Ngôn từ xúc phạm',
            'Đánh giá ảo, không có thật',
          ]"
          :error="reasonError"
          hint="Từ chối ghi một vi phạm cho tác giả (hết hạn sau 30 ngày). Tác giả nhận lý do trong thông báo."
        />
      </div>
    </template>
  </div>
</template>
