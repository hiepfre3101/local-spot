<script setup lang="ts">
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, onMounted, ref, useId } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { ownerApi } from '@/api/owner'
import { Permission } from '@/auth/permissions'
import LsRating from '@/components/place/LsRating.vue'
import ClaimForm from '@/components/owner/ClaimForm.vue'
import LsBadge from '@/components/ui/LsBadge.vue'
import LsButton from '@/components/ui/LsButton.vue'
import { useNotify } from '@/plugins/toast'
import { useAuthStore } from '@/stores/auth'
import type { ClaimStatus, PlaceDetail } from '@/types/api'
import { formatDate } from '@/utils/format'

/**
 * P09 · Địa điểm của tôi — `/owner` (sitemap). Ba khối:
 * - **Địa điểm tôi sở hữu** (`GET /owner/places`, chỉ khi có quyền chủ) → trang quản lý từng địa điểm.
 * - **Yêu cầu của tôi** (`GET /me/claims` — thêm 2026-10-18): đang chờ / được duyệt / bị từ chối kèm lý do, gửi lại.
 * - **Gửi yêu cầu xác nhận sở hữu** (UC23): hiện ngay khi chưa sở hữu gì; đã có địa điểm thì mở bằng nút (một chủ có
 *   nhiều địa điểm — sitemap). `?place=<slug>` (nút trên trang địa điểm) chọn sẵn địa điểm.
 *
 * Vào trang thì nạp lại `/me`: yêu cầu vừa được duyệt cấp role OWNER phía máy chủ ngay, quyền trong phiên phải theo.
 */
const uid = useId()
const auth = useAuthStore()
const route = useRoute()
const notify = useNotify()
const queryClient = useQueryClient()

const initialSlug = computed(() =>
  typeof route.query.place === 'string' && route.query.place ? route.query.place : null,
)
const isOwner = computed(() => auth.can(Permission.PLACE_UPDATE_OWN))

onMounted(() => {
  void auth.reloadUser().catch(() => undefined)
})

const places = useQuery({
  queryKey: ['owner', 'places'],
  queryFn: () => ownerApi.places(),
  enabled: isOwner,
})

const claims = useInfiniteQuery({
  queryKey: ['me', 'claims'],
  queryFn: ({ pageParam }) => ownerApi.myClaims(pageParam),
  initialPageParam: null as string | null,
  getNextPageParam: (last) => last.nextCursor,
})
const claimItems = computed(() => claims.data.value?.pages.flatMap((p) => p.items) ?? [])

const CLAIM_STATUS: Record<ClaimStatus, { tone: 'warning' | 'success' | 'danger'; text: string }> =
  {
    PENDING: { tone: 'warning', text: '● Đang chờ duyệt' },
    APPROVED: { tone: 'success', text: '✓ Đã xác nhận' },
    REJECTED: { tone: 'danger', text: '✕ Bị từ chối' },
  }

/** Form mở sẵn khi chưa sở hữu gì hoặc đến từ nút trên trang địa điểm. */
const formOpen = ref(false)
const showForm = computed(
  () =>
    formOpen.value ||
    !!initialSlug.value ||
    (places.isSuccess.value && !places.data.value?.length) ||
    !isOwner.value,
)
const formSlug = ref<string | null>(null)

function resubmit(slug: string): void {
  formSlug.value = slug
  formOpen.value = true
  document.getElementById(`${uid}-claim`)?.scrollIntoView({ behavior: 'smooth' })
}

function submitted(place: PlaceDetail): void {
  notify.success(`Đã gửi yêu cầu cho “${place.name}”. Bạn sẽ nhận thông báo khi có kết quả.`)
  formOpen.value = false
  formSlug.value = null
  void queryClient.invalidateQueries({ queryKey: ['me', 'claims'] })
  void queryClient.invalidateQueries({ queryKey: ['place', place.slug] })
}
</script>

<template>
  <article class="ls-pub py-8">
    <span class="ls-kick">Chủ địa điểm</span>
    <h1 class="ls-h1 mt-2">Địa điểm của tôi</h1>

    <!-- Địa điểm đang sở hữu -->
    <section v-if="isOwner" class="mt-8" :aria-labelledby="`${uid}-owned`">
      <h2 :id="`${uid}-owned`" class="ls-h2 text-title!">Đang quản lý</h2>
      <p v-if="places.isError.value" role="alert">✕ Chưa tải được địa điểm của bạn.</p>
      <p v-else-if="places.isSuccess.value && !places.data.value?.length" class="text-ink-muted">
        Bạn chưa sở hữu địa điểm nào.
      </p>
      <ul class="m-0 list-none p-0">
        <li
          v-for="p in places.data.value ?? []"
          :key="p.id"
          class="flex flex-wrap items-center justify-between gap-4 border-b border-line py-4"
        >
          <div class="min-w-0">
            <RouterLink
              class="font-semibold text-ink"
              :to="{ name: 'owner-place', params: { id: p.id } }"
              >{{ p.name }}</RouterLink
            >
            <p class="m-0 text-caption text-ink-muted">{{ p.address }}</p>
            <LsRating class="mt-1" :score="p.bayesianScore" :count="p.reviewCount" />
          </div>
          <LsButton variant="ghost" size="sm" :to="{ name: 'owner-place', params: { id: p.id } }"
            >Quản lý</LsButton
          >
        </li>
      </ul>
    </section>

    <!-- Yêu cầu đã gửi -->
    <section v-if="claimItems.length" class="mt-10" :aria-labelledby="`${uid}-claims`">
      <h2 :id="`${uid}-claims`" class="ls-h2 text-title!">Yêu cầu xác nhận sở hữu đã gửi</h2>
      <ul class="m-0 list-none p-0">
        <li
          v-for="c in claimItems"
          :key="c.id"
          class="flex flex-wrap items-start justify-between gap-3 border-b border-line py-4"
        >
          <div class="min-w-0">
            <RouterLink
              class="font-semibold text-ink"
              :to="{ name: 'place', params: { slug: c.place.slug } }"
              >{{ c.place.name }}</RouterLink
            >
            <p class="m-0 text-caption text-ink-muted">
              Gửi {{ formatDate(c.createdAt) }}
              <template v-if="c.reviewedAt"> · Xét {{ formatDate(c.reviewedAt) }}</template>
            </p>
            <p v-if="c.status === 'REJECTED' && c.rejectReason" class="m-0 mt-1">
              Lý do: {{ c.rejectReason }}
            </p>
          </div>
          <div class="flex flex-wrap items-center gap-3">
            <LsBadge :tone="CLAIM_STATUS[c.status].tone">{{ CLAIM_STATUS[c.status].text }}</LsBadge>
            <button
              v-if="c.status === 'REJECTED'"
              type="button"
              class="ls-act"
              @click="resubmit(c.place.slug)"
            >
              Gửi lại
            </button>
          </div>
        </li>
      </ul>
      <div v-if="claims.hasNextPage.value" class="pt-4">
        <LsButton
          variant="ghost"
          size="sm"
          :loading="claims.isFetchingNextPage.value"
          @click="claims.fetchNextPage()"
          >Xem thêm</LsButton
        >
      </div>
    </section>

    <!-- Gửi yêu cầu (UC23) -->
    <section
      :id="`${uid}-claim`"
      class="mt-12 grid gap-8 border-t border-line pt-10 md:grid-cols-[300px_1fr] md:gap-16"
      :aria-labelledby="`${uid}-claim-title`"
    >
      <div>
        <h2 :id="`${uid}-claim-title`" class="ls-h2 m-0 text-title!">Yêu cầu xác nhận sở hữu</h2>
        <p class="mt-2 text-caption text-ink-muted">
          Bạn là chủ quán? Gửi minh chứng để quản lý thông tin, xem thống kê và phản hồi đánh giá.
          Kiểm duyệt viên xem xét yêu cầu; bạn nhận thông báo khi có kết quả.
        </p>
      </div>
      <ClaimForm v-if="showForm" :initial-slug="formSlug ?? initialSlug" @submitted="submitted" />
      <div v-else>
        <LsButton variant="ghost" @click="formOpen = true">+ Yêu cầu sở hữu địa điểm khác</LsButton>
      </div>
    </section>
  </article>
</template>
