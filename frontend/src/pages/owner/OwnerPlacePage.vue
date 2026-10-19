<script setup lang="ts">
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { ownerApi } from '@/api/owner'
import { placesApi } from '@/api/places'
import OwnerPlaceForm from '@/components/owner/OwnerPlaceForm.vue'
import OwnerReviewList from '@/components/owner/OwnerReviewList.vue'
import OwnerStats from '@/components/owner/OwnerStats.vue'
import LsBadge from '@/components/ui/LsBadge.vue'
import LsButton from '@/components/ui/LsButton.vue'
import NotFoundPage from '@/pages/errors/NotFoundPage.vue'
import type { PlaceDetail } from '@/types/api'

/**
 * P09 · Quản lý địa điểm — `/owner/places/:id` (sitemap), tab `?tab=stats|info|reviews`: Thống kê (UC26) · Thông tin
 * địa điểm (UC24) · Đánh giá & phản hồi (UC25). Giao diện công khai (O-P09, chốt). Địa điểm phải nằm trong
 * `GET /owner/places` (chủ hiện tại) — không thì trang không tìm thấy, như backend trả 404 / 403.
 *
 * Bản mẫu có nút "Cập nhật thông tin" ở đầu trang — ở đây là tab Thông tin (cùng việc, không lặp hai lối vào).
 */
const props = defineProps<{ id: number }>()

type Tab = 'stats' | 'info' | 'reviews'
const TABS: { key: Tab; label: string }[] = [
  { key: 'stats', label: 'Thống kê' },
  { key: 'info', label: 'Thông tin địa điểm' },
  { key: 'reviews', label: 'Đánh giá & phản hồi' },
]

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()

const owned = useQuery({ queryKey: ['owner', 'places'], queryFn: () => ownerApi.places() })
const summary = computed(() => owned.data.value?.find((p) => p.id === props.id) ?? null)
const notFound = computed(() => owned.isSuccess.value && !summary.value)

const detail = useQuery({
  queryKey: computed(() => ['place', summary.value?.slug ?? null]),
  queryFn: () => placesApi.detail(summary.value!.slug),
  enabled: computed(() => !!summary.value),
})
const place = computed(() => detail.data.value)

watch(
  place,
  (p) => {
    if (p) document.title = `${p.name} · Địa điểm của tôi · LocalSpot`
  },
  { immediate: true },
)

const tab = computed<Tab>(() => {
  const requested = route.query.tab
  return TABS.some((t) => t.key === requested) ? (requested as Tab) : 'stats'
})
const showAll = computed(() => route.query.filter === 'all')
function setFilter(all: boolean): void {
  void router.replace({ query: { tab: 'reviews', filter: all ? 'all' : undefined } })
}

/** Sau khi lưu: cập nhật trang chi tiết (dùng chung khoá với P03) + danh sách của chủ. */
function saved(next: PlaceDetail): void {
  if (next !== place.value) queryClient.setQueryData(['place', next.slug], next)
  void queryClient.invalidateQueries({ queryKey: ['place', next.slug] })
  void queryClient.invalidateQueries({ queryKey: ['owner', 'places'] })
}
</script>

<template>
  <NotFoundPage v-if="notFound" />

  <section v-else-if="owned.isError.value || detail.isError.value" class="py-16" role="alert">
    <h1 class="ls-h1">Chưa tải được địa điểm</h1>
    <p class="mt-4 text-ink-muted">Kiểm tra kết nối mạng rồi thử lại.</p>
    <LsButton class="mt-4" @click="owned.isError.value ? owned.refetch() : detail.refetch()"
      >Thử lại</LsButton
    >
  </section>

  <div v-else-if="!place" aria-busy="true" aria-label="Đang tải địa điểm" class="py-8">
    <span class="ls-skel mb-3 h-4 w-40"></span>
    <span class="ls-skel mb-4 h-11 w-1/2"></span>
    <span class="ls-skel h-24 w-full"></span>
  </div>

  <article v-else class="ls-pub py-8">
    <nav aria-label="Vị trí trang" class="ls-crumb">
      <RouterLink :to="{ name: 'owner' }">Địa điểm của tôi</RouterLink>
    </nav>
    <header class="mb-6">
      <h1 class="ls-h1 m-0 [overflow-wrap:anywhere]">{{ place.name }}</h1>
      <div class="mt-3 flex flex-wrap items-center gap-3">
        <LsBadge tone="info">✓ Đã xác nhận sở hữu</LsBadge>
        <RouterLink
          class="ls-link text-caption"
          :to="{ name: 'place', params: { slug: place.slug } }"
          >Xem trang công khai →</RouterLink
        >
      </div>
    </header>

    <nav class="ls-tabs" aria-label="Quản lý địa điểm">
      <RouterLink
        v-for="t in TABS"
        :key="t.key"
        :to="{ query: { tab: t.key === 'stats' ? undefined : t.key } }"
        :aria-current="tab === t.key ? 'page' : undefined"
        replace
      >
        {{ t.label }}
      </RouterLink>
    </nav>

    <OwnerStats v-if="tab === 'stats'" :place-id="place.id" />

    <OwnerPlaceForm v-else-if="tab === 'info'" :place="place" @saved="saved" />

    <section v-else class="max-w-[760px]">
      <div class="mb-4 flex flex-wrap gap-2" role="group" aria-label="Lọc đánh giá">
        <button type="button" class="ls-fchip" :aria-pressed="!showAll" @click="setFilter(false)">
          Cần phản hồi
        </button>
        <button type="button" class="ls-fchip" :aria-pressed="showAll" @click="setFilter(true)">
          Tất cả đánh giá
        </button>
      </div>
      <OwnerReviewList :key="String(showAll)" :place-id="place.id" :unreplied="!showAll" />
    </section>
  </article>
</template>
