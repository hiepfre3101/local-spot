<script setup lang="ts">
import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/vue-query'
import { isAxiosError } from 'axios'
import { computed, defineAsyncComponent, ref, useTemplateRef, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { placesApi, type ReviewSort } from '@/api/places'
import { reviewsApi } from '@/api/reviews'
import LsRating from '@/components/place/LsRating.vue'
import PlacePhoto from '@/components/place/PlacePhoto.vue'
import DirectionsDialog from '@/components/place/DirectionsDialog.vue'
import ReportDialog from '@/components/place/ReportDialog.vue'
import type { ReportTarget } from '@/components/place/reportTarget'
import ReviewItem from '@/components/place/ReviewItem.vue'
import SpotCard from '@/components/place/SpotCard.vue'
import LsAvatar from '@/components/ui/LsAvatar.vue'
import LsBadge from '@/components/ui/LsBadge.vue'
import LsButton from '@/components/ui/LsButton.vue'
import LsCarousel from '@/components/ui/LsCarousel.vue'
import { useRequireLogin } from '@/composables/useRequireLogin'
import NotFoundPage from '@/pages/errors/NotFoundPage.vue'
import { useAuthStore } from '@/stores/auth'
import { useFilterStore } from '@/stores/filter'
import { formatNumber, formatPriceRange, tagLabel } from '@/utils/format'
import { openState, weekSchedule } from '@/utils/openingHours'
import { scoreText } from '@/utils/rating'

/**
 * P03 · Chi tiết địa điểm (UC10) — bản đọc của đợt khám phá. Thứ tự theo bản sửa 29/09: breadcrumb → thư viện ảnh → tên,
 * Rating, danh mục, thẻ → Giới thiệu (+ đánh giá nổi bật) | bản đồ nhỏ, địa chỉ, giờ, liên hệ, tiện ích → địa điểm tương
 * tự → tất cả đánh giá → gần đây.
 *
 * Viết / sửa đánh giá (P04): "Viết đánh giá" khi chưa có, "Sửa đánh giá của bạn" khi đã có (`myReviewId`); chủ địa điểm
 * không thấy nút (không tự đánh giá — UC12).
 *
 * Tương tác (2026-10-17): vote / bình luận / báo cáo đánh giá trong ReviewItem; "Báo cáo" địa điểm ở đầu trang; một
 * hộp thoại báo cáo dùng chung (UC14, UC15, UC17). Đánh giá chờ duyệt / bị từ chối của chính mình ghim đầu danh sách.
 *
 * Chưa có ở đợt này (không có link chết — nút chỉ hiện khi đã có route / API): Check-in (P12), Lưu (UC18), yêu cầu sở
 * hữu (UC23). Khối "Ảnh check-in" bỏ: check-in không có ảnh trong mô hình dữ liệu (U5) — ảnh review đã hiện trong
 * từng đánh giá.
 */
const props = defineProps<{ slug: string }>()

const PlaceMap = defineAsyncComponent(() => import('@/components/place/PlaceMap.vue'))
const router = useRouter()
const filter = useFilterStore()

const detail = useQuery({
  queryKey: computed(() => ['place', props.slug]),
  queryFn: () => placesApi.detail(props.slug),
})
const place = computed(() => detail.data.value)
const notFound = computed(
  () =>
    detail.isError.value &&
    isAxiosError(detail.error.value) &&
    detail.error.value.response?.status === 404,
)

watch(
  place,
  (p) => {
    if (p) document.title = `${p.name} · LocalSpot`
  },
  { immediate: true },
)

// ─── Đầu trang ──────────────────────────────────────────────────────────────
const gallery = computed(() => (place.value?.photos ?? []).filter((p) => p.status === 'READY'))
const galleryDialog = useTemplateRef<HTMLDialogElement>('galleryDialog')
const backToResults = computed(() => filter.lastQuery)
const price = computed(() =>
  place.value ? formatPriceRange(place.value.priceMin, place.value.priceMax) : null,
)

// ─── Giờ mở cửa (giờ Việt Nam) ─────────────────────────────────────────────
const now = ref(new Date())
const open = computed(() => (place.value ? openState(place.value.openingHours, now.value) : null))
const schedule = computed(() => (place.value ? weekSchedule(place.value.openingHours) : []))

// ─── Đánh giá nổi bật: nhiều lượt hữu ích, từ 4 sao ─────────────────────────
const placeId = computed(() => place.value?.id ?? null)
const highlights = useQuery({
  queryKey: computed(() => ['place', placeId.value, 'reviews', 'highlights']),
  queryFn: () => placesApi.reviews(placeId.value!, { sort: 'HELPFUL', limit: 20 }),
  enabled: computed(() => placeId.value !== null && (place.value?.reviewCount ?? 0) > 0),
  select: (page) => page.items.filter((r) => r.rating >= 4 && r.helpfulCount > 0).slice(0, 12),
})

// ─── Tất cả đánh giá ────────────────────────────────────────────────────────
const reviewSort = ref<ReviewSort>('HELPFUL')
const ratingFilter = ref<number | null>(null)
const reviews = useInfiniteQuery({
  queryKey: computed(() => [
    'place',
    placeId.value,
    'reviews',
    reviewSort.value,
    ratingFilter.value,
  ]),
  queryFn: ({ pageParam }) =>
    placesApi.reviews(placeId.value!, {
      sort: reviewSort.value,
      rating: ratingFilter.value ?? undefined,
      cursor: pageParam,
      limit: 10,
    }),
  initialPageParam: null as string | null,
  getNextPageParam: (last) => last.nextCursor,
  enabled: computed(() => placeId.value !== null),
  placeholderData: keepPreviousData,
})
const reviewItems = computed(() => reviews.data.value?.pages.flatMap((p) => p.items) ?? [])
/**
 * Đánh giá của chính tôi khi chưa đăng (chờ duyệt / bị từ chối): danh sách công khai chỉ có PUBLISHED nên nạp riêng,
 * hiện ở đầu danh sách (ReviewItem README). Đã đăng thì nó nằm sẵn trong danh sách.
 */
const myReview = useQuery({
  queryKey: computed(() => ['review', place.value?.myReviewId ?? null]),
  queryFn: () => reviewsApi.get(place.value!.myReviewId!),
  enabled: computed(() => (place.value?.myReviewId ?? null) !== null),
  retry: false,
})
const myUnpublished = computed(() => {
  const r = myReview.data.value
  return r && (r.status === 'PENDING' || r.status === 'REJECTED') ? r : null
})

// ─── Báo cáo (UC17): một hộp thoại cho địa điểm / đánh giá / bình luận ─────
const reportDialog = useTemplateRef<InstanceType<typeof ReportDialog>>('reportDialog')
const requireLogin = useRequireLogin()
function openReport(target: ReportTarget): void {
  reportDialog.value?.open(target)
}
function reportPlace(): void {
  if (!requireLogin() || !place.value) return
  openReport({ type: 'PLACE', id: place.value.id, label: place.value.name })
}
const distribution = computed(() => {
  const d = place.value?.ratingDistribution
  const total = place.value?.reviewCount ?? 0
  return [5, 4, 3, 2, 1].map((star) => {
    const count = d?.[String(star) as '1'] ?? 0
    return { star, count, percent: total > 0 ? Math.round((count / total) * 100) : 0 }
  })
})

function filterByRating(star: number): void {
  ratingFilter.value = ratingFilter.value === star ? null : star
}

// ─── Tương tự (cùng danh mục, cùng thành phố) & gần đây ────────────────────
const similar = useQuery({
  queryKey: computed(() => ['place', placeId.value, 'similar']),
  queryFn: () =>
    placesApi.list(
      { categoryId: place.value!.category.id, city: place.value!.city },
      'SCORE',
      null,
      5,
    ),
  enabled: computed(() => place.value?.status === 'APPROVED'),
  select: (page) => page.items.filter((p) => p.id !== placeId.value).slice(0, 4),
})
const nearby = useQuery({
  queryKey: computed(() => ['place', placeId.value, 'nearby']),
  queryFn: () =>
    placesApi.nearby({
      lat: place.value!.location.lat,
      lng: place.value!.location.lng,
      radius: 5000,
      limit: 13,
    }),
  enabled: computed(() => place.value?.status === 'APPROVED'),
  select: (items) => items.filter((p) => p.id !== placeId.value).slice(0, 12),
})

/** Chỉ đường trong ứng dụng (hộp thoại bản đồ lớn), không chuyển sang trang ngoài — chốt 2026-10-13. */
const directions = useTemplateRef<InstanceType<typeof DirectionsDialog>>('directions')
const auth = useAuthStore()
/** `null` = không hiện nút: chủ địa điểm, địa điểm chưa duyệt. Khách bấm thì guard đưa qua đăng nhập. */
const reviewAction = computed<'write' | 'edit' | null>(() => {
  const p = place.value
  if (!p || !router.hasRoute('write-review')) return null
  if (p.myReviewId !== null) return 'edit'
  if (p.status !== 'APPROVED' || auth.user?.ownedPlaceIds.includes(p.id)) return null
  return 'write'
})
</script>

<template>
  <NotFoundPage v-if="notFound" />

  <section v-else-if="detail.isError.value" class="py-16" role="alert">
    <h1 class="ls-h1">Chưa tải được địa điểm</h1>
    <p class="mt-4 text-ink-muted">Kiểm tra kết nối mạng rồi thử lại.</p>
    <LsButton class="mt-4" @click="detail.refetch()">Thử lại</LsButton>
  </section>

  <div v-else-if="!place" aria-busy="true" aria-label="Đang tải địa điểm">
    <span class="ls-skel mb-3 h-4 w-48"></span>
    <div class="mb-8 grid h-[368px] grid-cols-[2fr_1fr] grid-rows-2 gap-2">
      <span class="ls-skel row-span-2"></span><span class="ls-skel"></span
      ><span class="ls-skel"></span>
    </div>
    <span class="ls-skel mb-4 h-11 w-2/3"></span>
    <span class="ls-skel h-5 w-1/3"></span>
  </div>

  <article v-else class="ls-pub">
    <!-- Breadcrumb -->
    <nav aria-label="Vị trí trang" class="ls-crumb">
      <RouterLink v-if="backToResults" :to="{ name: 'search', query: backToResults }"
        >← Kết quả tìm kiếm</RouterLink
      >
      <template v-if="backToResults"> · </template>
      <RouterLink :to="{ name: 'search' }">Khám phá</RouterLink> / {{ place.city }} /
      <RouterLink :to="{ name: 'search', query: { category: place.category.slug } }">{{
        place.category.name
      }}</RouterLink>
    </nav>

    <p v-if="place.status === 'PENDING'" class="mb-4" role="status">
      <LsBadge tone="warning">● Đang chờ duyệt — chỉ bạn và kiểm duyệt viên thấy</LsBadge>
    </p>
    <p v-else-if="place.status === 'REJECTED'" class="mb-4" role="status">
      <LsBadge tone="danger">✕ Đề xuất bị từ chối</LsBadge>
      <span v-if="place.rejectReason" class="ml-2 text-ink-muted"
        >Lý do: {{ place.rejectReason }}</span
      >
    </p>

    <!-- Thư viện ảnh -->
    <div class="mb-8 grid gap-2 sm:h-[368px] sm:grid-cols-[2fr_1fr] sm:grid-rows-2">
      <PlacePhoto
        :photo="gallery[0] ?? place.coverPhoto"
        :alt="`Ảnh ${place.name}`"
        eager
        sizes="(min-width: 640px) 66vw, 100vw"
        class="h-[240px] rounded-md sm:row-span-2 sm:h-auto"
      >
        <button
          v-if="gallery.length > 1"
          type="button"
          class="absolute right-3 bottom-3 cursor-pointer rounded-pill border-0 bg-surface-raised px-3 py-1.5 text-caption font-semibold text-ink"
          @click="galleryDialog?.showModal()"
        >
          Xem {{ formatNumber(gallery.length) }} ảnh
        </button>
      </PlacePhoto>
      <PlacePhoto :photo="gallery[1] ?? null" sizes="33vw" class="rounded-md max-sm:hidden" />
      <PlacePhoto :photo="gallery[2] ?? null" sizes="33vw" class="rounded-md max-sm:hidden" />
    </div>
    <dialog
      ref="galleryDialog"
      class="ls-pop m-auto max-h-[90vh] w-[min(1100px,calc(100vw-32px))] p-0 text-ink backdrop:bg-ink/60"
      aria-label="Ảnh địa điểm"
      @click.self="galleryDialog?.close()"
    >
      <div class="flex items-center justify-between border-b border-line p-4">
        <h2 class="m-0 text-title">Ảnh · {{ place.name }}</h2>
        <LsButton variant="ghost" size="sm" @click="galleryDialog?.close()">Đóng</LsButton>
      </div>
      <div class="grid gap-2 overflow-y-auto p-4 sm:grid-cols-2 lg:grid-cols-3">
        <PlacePhoto
          v-for="(photo, i) in gallery"
          :key="photo.id"
          :photo="photo"
          :alt="`Ảnh ${i + 1} — ${place.name}`"
          sizes="(min-width: 1024px) 360px, 50vw"
          class="aspect-[4/3] rounded-md"
        />
      </div>
    </dialog>

    <!-- Tên, điểm, thẻ -->
    <header
      class="flex flex-col justify-between gap-6 border-b border-line pb-8 md:flex-row md:items-end"
    >
      <div class="min-w-0">
        <h1 class="ls-h1 detail-title">{{ place.name }}</h1>
        <div class="mt-3 flex flex-wrap items-center gap-4">
          <LsRating :score="place.bayesianScore" :count="place.reviewCount" size="lg" />
          <LsBadge v-if="place.owner" tone="info">✓ Đã xác nhận sở hữu</LsBadge>
        </div>
        <p class="mt-3 mb-2 text-ink-muted">{{ place.category.name }} · {{ place.address }}</p>
        <ul v-if="place.tags.length" class="ls-tags text-caption!" aria-label="Thẻ">
          <li v-for="tag in place.tags" :key="tag">{{ tagLabel(tag) }}</li>
        </ul>
      </div>
      <div class="flex flex-wrap gap-2">
        <LsButton
          v-if="reviewAction"
          :variant="reviewAction === 'write' ? 'accent' : 'ghost'"
          :to="{ name: 'write-review', params: { slug: place.slug } }"
          >{{ reviewAction === 'write' ? 'Viết đánh giá' : 'Sửa đánh giá của bạn' }}</LsButton
        >
        <LsButton variant="ghost" @click="reportPlace">Báo cáo</LsButton>
      </div>
    </header>

    <!-- Giới thiệu | thông tin -->
    <div class="grid gap-12 pt-8 lg:grid-cols-[1fr_340px] lg:gap-16">
      <div class="min-w-0">
        <span class="ls-kick">Giới thiệu</span>
        <p v-if="place.description" class="about mt-2 max-w-[62ch]">{{ place.description }}</p>
        <p v-else class="mt-2 text-ink-muted">Địa điểm chưa có mô tả.</p>

        <div v-if="highlights.data.value?.length" class="mt-8 border-t border-line pt-6">
          <LsCarousel
            title="Đánh giá nổi bật"
            subtitle="Điểm cao và nhiều lượt hữu ích nhất"
            :count="highlights.data.value.length"
            :per="2"
            heading-level="h2"
          >
            <article v-for="review in highlights.data.value" :key="review.id" class="ls-rmini">
              <LsRating :score="review.rating" />
              <q>{{
                review.content.length > 220
                  ? `${review.content.slice(0, 217).trimEnd()}…`
                  : review.content
              }}</q>
              <footer>
                <LsAvatar
                  :name="review.author.displayName"
                  :src="review.author.avatarUrl"
                  size="sm"
                />
                <span>
                  <b>{{ review.author.displayName }}</b>
                  <template v-if="review.authorCheckedIn">✓ Đã check-in · </template>
                  {{ formatNumber(review.helpfulCount) }} hữu ích
                </span>
              </footer>
            </article>
          </LsCarousel>
        </div>
      </div>

      <aside class="flex flex-col gap-4" aria-label="Thông tin địa điểm">
        <div class="relative">
          <PlaceMap class="h-[180px] rounded-md" :places="[place]" compact />
          <button
            type="button"
            class="absolute inset-0 z-[500] cursor-pointer rounded-md border-0 bg-transparent"
            aria-label="Mở bản đồ lớn và chỉ đường"
            @click="directions?.open()"
          ></button>
        </div>
        <DirectionsDialog ref="directions" :place="place" />
        <ul class="info m-0 list-none border-t border-line p-0">
          <li>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 21s-7-6.3-7-12a7 7 0 0 1 14 0c0 5.7-7 12-7 12z" />
              <circle cx="12" cy="9" r="2.5" />
            </svg>
            <span>
              {{ place.address }} ·
              <button
                type="button"
                class="ls-link cursor-pointer border-0 bg-transparent p-0"
                @click="directions?.open()"
              >
                Chỉ đường
              </button>
            </span>
          </li>
          <li>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 7v5l3 2" />
            </svg>
            <span v-if="!open">Chưa có giờ mở cửa</span>
            <details v-else class="w-full">
              <summary class="cursor-pointer">
                <b>{{ open.open ? '● Đang mở' : '○ Đã đóng' }}</b>
                <template v-if="open.today"> · Hôm nay {{ open.today }}</template>
                <template v-else> · Hôm nay nghỉ</template>
              </summary>
              <table class="mt-2 w-full text-caption">
                <caption class="sr-only">
                  Giờ mở cửa trong tuần
                </caption>
                <tbody>
                  <tr v-for="day in schedule" :key="day.dayOfWeek">
                    <th scope="row" class="py-0.5 pr-3 text-left font-normal text-ink-muted">
                      {{ day.name }}
                    </th>
                    <td class="py-0.5">{{ day.slots || 'Nghỉ' }}</td>
                  </tr>
                </tbody>
              </table>
            </details>
          </li>
          <li v-if="place.phone">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"
              />
            </svg>
            <a class="ls-link" :href="`tel:${place.phone.replace(/\s+/g, '')}`">{{
              place.phone
            }}</a>
          </li>
          <li v-if="place.website">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="9" />
              <path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18" />
            </svg>
            <a
              class="ls-link break-all"
              :href="place.website"
              target="_blank"
              rel="noopener nofollow ugc"
            >
              {{ place.website.replace(/^https?:\/\//, '') }}
            </a>
          </li>
          <li v-if="price">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <rect x="3" y="6" width="18" height="12" rx="2" />
              <circle cx="12" cy="12" r="2.5" />
            </svg>
            <span>{{ price }} / người</span>
          </li>
          <li v-if="place.amenities.length">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12l4 4 10-10" /></svg>
            <span>{{ place.amenities.map((a) => a.name).join(' · ') }}</span>
          </li>
          <li v-if="place.checkinCount > 0">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 21s-7-6.3-7-12a7 7 0 0 1 14 0c0 5.7-7 12-7 12z" />
              <path d="M9 9l2 2 4-4" />
            </svg>
            <span>{{ formatNumber(place.checkinCount) }} lượt check-in</span>
          </li>
        </ul>
      </aside>
    </div>

    <!-- Địa điểm tương tự -->
    <section v-if="similar.data.value?.length" class="sec" aria-labelledby="similar-title">
      <div class="ls-car__head">
        <div>
          <h2 id="similar-title">Địa điểm tương tự</h2>
          <p>Cùng danh mục ở {{ place.city }}, điểm cao trước</p>
        </div>
        <RouterLink
          class="ls-link text-caption"
          :to="{ name: 'search', query: { category: place.category.slug } }"
        >
          Xem thêm →
        </RouterLink>
      </div>
      <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        <SpotCard v-for="p in similar.data.value" :key="p.id" :place="p" />
      </div>
    </section>

    <!-- Tất cả đánh giá -->
    <section id="reviews" class="sec" aria-labelledby="reviews-title">
      <h2 id="reviews-title" class="ls-h2">Tất cả đánh giá</h2>
      <p v-if="place.reviewCount === 0" class="text-ink-muted">
        Chưa có đánh giá nào được duyệt. Hãy là người đầu tiên kể về nơi này.
      </p>
      <template v-else>
        <div
          class="grid max-w-[760px] items-center gap-6 border-b border-line pb-6 sm:grid-cols-[200px_1fr] sm:gap-10"
        >
          <div>
            <div class="big-score">{{ scoreText(place.bayesianScore) }}</div>
            <LsRating :score="place.bayesianScore" />
            <p class="mt-2 mb-0 text-caption text-ink-muted">
              {{ formatNumber(place.reviewCount) }} đánh giá đã duyệt · trung bình thô
              {{ scoreText(place.avgRating).replace('.', ',') }}
            </p>
          </div>
          <div class="ls-dist" role="group" aria-label="Lọc theo số sao">
            <template v-for="row in distribution" :key="row.star">
              <button
                type="button"
                class="cursor-pointer border-0 bg-transparent p-0 text-left text-ink"
                :aria-pressed="ratingFilter === row.star"
                :aria-label="`${row.star} sao: ${formatNumber(row.count)} đánh giá — lọc`"
                :class="ratingFilter === row.star && 'font-bold underline'"
                @click="filterByRating(row.star)"
              >
                {{ row.star }}
              </button>
              <span class="bar" aria-hidden="true"
                ><i :style="{ width: `${row.percent}%` }"></i
              ></span>
              <span class="c">{{ formatNumber(row.count) }}</span>
            </template>
          </div>
        </div>
        <p class="mt-3 max-w-[760px] text-caption text-ink-muted">
          Điểm hiển thị đã điều chỉnh theo số lượt đánh giá (trung bình Bayesian) để vài đánh giá
          đầu không quyết định thứ hạng.
        </p>

        <div class="mt-6 flex flex-wrap gap-2" role="group" aria-label="Sắp xếp đánh giá">
          <button
            class="ls-fchip"
            type="button"
            :aria-pressed="reviewSort === 'HELPFUL'"
            @click="reviewSort = 'HELPFUL'"
          >
            Hữu ích nhất
          </button>
          <button
            class="ls-fchip"
            type="button"
            :aria-pressed="reviewSort === 'NEWEST'"
            @click="reviewSort = 'NEWEST'"
          >
            Mới nhất
          </button>
          <button
            v-if="ratingFilter !== null"
            class="ls-fchip"
            type="button"
            aria-pressed="true"
            @click="ratingFilter = null"
          >
            {{ ratingFilter }} sao ✕
          </button>
        </div>

        <div class="max-w-[760px]" :aria-busy="reviews.isFetching.value">
          <p v-if="reviews.isError.value" class="py-6" role="alert">
            ✕ Chưa tải được đánh giá.
            <button
              class="ls-link cursor-pointer border-0 bg-transparent p-0"
              type="button"
              @click="reviews.refetch()"
            >
              Thử lại
            </button>
          </p>
          <p
            v-else-if="!reviews.isPending.value && reviewItems.length === 0"
            class="py-6 text-ink-muted"
          >
            Không có đánh giá {{ ratingFilter }} sao.
          </p>
          <ReviewItem
            v-if="myUnpublished"
            :review="myUnpublished"
            :place-slug="place.slug"
            @report="openReport"
          />
          <ReviewItem
            v-for="review in reviewItems"
            :key="review.id"
            :review="review"
            :place-slug="place.slug"
            @report="openReport"
          />
          <div v-if="reviews.hasNextPage.value" class="flex justify-center pt-6">
            <LsButton
              variant="ghost"
              :loading="reviews.isFetchingNextPage.value"
              @click="reviews.fetchNextPage()"
            >
              {{ reviews.isFetchingNextPage.value ? 'Đang tải…' : 'Xem thêm đánh giá' }}
            </LsButton>
          </div>
        </div>
      </template>
    </section>

    <!-- Gần đây -->
    <div v-if="nearby.data.value?.length" class="sec">
      <LsCarousel
        :title="`Gần đây ở ${place.city}`"
        subtitle="Trong bán kính 5 km, gần nhất trước"
        :count="nearby.data.value.length"
        :per="4"
      >
        <SpotCard v-for="p in nearby.data.value" :key="p.id" :place="p" />
      </LsCarousel>
    </div>
    <ReportDialog ref="reportDialog" />
  </article>
</template>

<style scoped>
/* Giá trị theo mẫu P03 (preview.html) */
.detail-title {
  font-size: 32px;
  overflow-wrap: anywhere;
}
@media (min-width: 768px) {
  .detail-title {
    font-size: 44px;
  }
}
.about {
  font: 400 17px/28px var(--font-sans);
  white-space: pre-line;
}
.big-score {
  font: 800 56px/1 var(--font-sans);
  margin-bottom: var(--space-2);
}
.sec {
  margin-top: 56px;
  padding-top: 40px;
  border-top: 1px solid var(--line);
}
.info li {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  font: 400 14px/20px var(--font-sans);
}
.info svg {
  flex: none;
  width: 18px;
  height: 18px;
  margin-top: 1px;
  fill: none;
  stroke: var(--ink-muted);
  stroke-width: 1.8;
}
</style>
