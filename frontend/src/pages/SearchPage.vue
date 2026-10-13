<script setup lang="ts">
import { storeToRefs } from 'pinia'
import {
  computed,
  defineAsyncComponent,
  nextTick,
  onBeforeUnmount,
  ref,
  useTemplateRef,
  watch,
} from 'vue'
import { useRoute } from 'vue-router'

import PlaceRow from '@/components/place/PlaceRow.vue'
import LsButton from '@/components/ui/LsButton.vue'
import { useAmenities, useCategories } from '@/composables/catalogQueries'
import { useSearchResults, type SearchInputs } from '@/composables/useSearchResults'
import { useFilterStore } from '@/stores/filter'
import { usePlaceStore } from '@/stores/place'
import type { GeoPoint } from '@/types/api'
import { formatNumber } from '@/utils/format'
import { formatDistance } from '@/utils/geo'
import {
  MAX_QUERY_LENGTH,
  MIN_RATING_OPTIONS,
  PRICE_OPTIONS,
  RADIUS_OPTIONS,
  effectiveSort,
  sortOptions,
  type SearchState,
} from '@/utils/searchQuery'

/**
 * P02 · Tìm kiếm & bản đồ (UC08, UC09). Thanh lọc một hàng → danh sách hàng gọn + bản đồ dính khi cuộn (sitemap §4:
 * bản đồ luôn cạnh danh sách). Mọi bộ lọc nằm trên URL (store `filter`).
 *
 * "Quanh tôi" / "Tìm trong vùng này" dùng `/places/nearby` — **chỉ lọc danh mục + bán kính** (UC09, chốt 2026-10-12):
 * ô từ khóa và các chip điểm / giá / tiện ích tắt kèm một dòng giải thích, không lặng lẽ bỏ qua.
 */
const PlaceMap = defineAsyncComponent(() => import('@/components/place/PlaceMap.vue'))

const route = useRoute()
const filter = useFilterStore()
const placeStore = usePlaceStore()
const { activePlaceId, location, locateStatus } = storeToRefs(placeStore)
const categories = useCategories()
const amenities = useAmenities()

const state = computed(() => filter.state)
const nearMode = computed(() => state.value.near !== null)

/** Slug trên URL → id cho API; slug lạ (URL gõ tay / danh mục đã xóa) bị bỏ qua. */
const selectedCategory = computed(() =>
  state.value.category ? categories.bySlug.value.get(state.value.category) : undefined,
)
const inputs = computed<SearchInputs>(() => ({
  state: state.value,
  categoryId: state.value.category
    ? categories.data.value
      ? selectedCategory.value?.id
      : null
    : undefined,
  amenityIds:
    state.value.amenities.length === 0
      ? []
      : amenities.data.value
        ? state.value.amenities
            .map((slug) => amenities.bySlug.value.get(slug)?.id)
            .filter((id): id is number => id !== undefined)
        : null,
  center: state.value.near === 'me' ? location.value : (state.value.near as GeoPoint | null),
}))
const results = useSearchResults(inputs)

// ─── Vị trí người dùng (near=me) ──────────────────────────────────────────
watch(
  () => state.value.near,
  (near) => {
    if (near === 'me') void placeStore.locate()
  },
  { immediate: true },
)

// ─── Thanh lọc ─────────────────────────────────────────────────────────────
const q = ref(state.value.q)
watch(
  () => state.value.q,
  (value) => (q.value = value),
)

function submitQuery(): void {
  void filter.update({ q: q.value.trim() }, 'push')
}

function setCategory(slug: string | null): void {
  void filter.update({ category: slug })
}

function toggleAmenity(slug: string): void {
  const set = new Set(state.value.amenities)
  if (set.has(slug)) set.delete(slug)
  else set.add(slug)
  void filter.update({ amenities: [...set] })
}

function toggleNear(): void {
  void filter.update({ near: nearMode.value ? null : 'me' }, 'push')
}

function onSelectNumber(key: 'minRating' | 'price' | 'radius', event: Event): void {
  const raw = (event.target as HTMLSelectElement).value
  if (key === 'radius') void filter.update({ radius: Number(raw) })
  else void filter.update({ [key]: raw === '' ? null : Number(raw) } as Partial<SearchState>)
}

function onSort(event: Event): void {
  void filter.update({ sort: (event.target as HTMLSelectElement).value as SearchState['sort'] })
}

function clearFilters(): void {
  void filter.update({ category: null, minRating: null, price: null, amenities: [] })
}

const hasExtraFilters = computed(
  () =>
    state.value.category !== null ||
    state.value.minRating !== null ||
    state.value.price !== null ||
    state.value.amenities.length > 0,
)
const roots = computed(() => categories.data.value ?? [])
/** Danh mục con đang chọn không có chip gốc riêng → hiện thêm một chip cho nó. */
const childChip = computed(() =>
  selectedCategory.value && !roots.value.some((r) => r.id === selectedCategory.value!.id)
    ? selectedCategory.value
    : null,
)

const SORT_LABELS: Record<string, string> = {
  RELEVANCE: 'Liên quan nhất',
  SCORE: 'Điểm cao nhất',
  NEWEST: 'Mới nhất',
  MOST_REVIEWED: 'Nhiều đánh giá',
}
const sorts = computed(() => sortOptions(results.mode.value))
const currentSort = computed(() => effectiveSort(state.value))

const heading = computed(() => {
  if (results.mode.value === 'nearby') {
    const n = results.items.value.length
    const where = state.value.near === 'me' ? 'quanh bạn' : 'trong vùng đã chọn'
    return `${results.nearbyCapped.value ? `${n}+` : formatNumber(n)} địa điểm ${where} · ${formatDistance(state.value.radius)}`
  }
  if (results.mode.value === 'search') return `Kết quả cho “${state.value.q}”`
  return selectedCategory.value ? selectedCategory.value.name : 'Tất cả địa điểm'
})

// ─── Danh sách ↔ bản đồ ────────────────────────────────────────────────────
const list = useTemplateRef<HTMLElement>('list')

function selectFromMap(placeId: number): void {
  placeStore.setActive(placeId)
  void nextTick(() => {
    list.value
      ?.querySelector(`[data-place-id="${placeId}"]`)
      ?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  })
}

/** "Tìm trong vùng này" chỉ hiện sau khi người dùng kéo / thu phóng bản đồ (P02). */
const movedView = ref<{ center: GeoPoint; radius: number } | null>(null)
/**
 * Canh lại bản đồ khi **kết quả mới** về (id của trang đầu đổi) — không canh khi tải thêm trang (trang đầu giữ nguyên)
 * hay khi người dùng đang tự kéo bản đồ.
 */
const fitKey = computed(() =>
  results.items.value
    .slice(0, 20)
    .map((p) => p.id)
    .join(','),
)
watch(
  () => route.query,
  () => (movedView.value = null),
)

function searchThisArea(): void {
  const view = movedView.value
  if (!view) return
  movedView.value = null
  void filter.update({ near: view.center, radius: view.radius, q: '' }, 'push')
}

// ─── Infinite scroll: tự tải trang sau khi chạm cuối danh sách; nút "Xem thêm" cho bàn phím / trình đọc màn hình ──
const sentinel = useTemplateRef<HTMLElement>('sentinel')
let observer: IntersectionObserver | null = null
watch(sentinel, (el) => {
  observer?.disconnect()
  if (!el || typeof IntersectionObserver === 'undefined') return
  observer = new IntersectionObserver(
    (entries) => {
      if (
        entries.some((e) => e.isIntersecting) &&
        results.hasNextPage.value &&
        !results.isFetchingNextPage.value
      ) {
        void results.fetchNextPage()
      }
    },
    { rootMargin: '400px 0px' },
  )
  observer.observe(el)
})
onBeforeUnmount(() => {
  observer?.disconnect()
  placeStore.setActive(null)
})

watch(
  () => route.query,
  (query) => filter.remember(query),
  { immediate: true },
)
</script>

<template>
  <div class="ls-pub">
    <h1 class="sr-only">Tìm kiếm địa điểm</h1>

    <!-- Thanh lọc -->
    <div class="mb-6 flex flex-wrap items-center gap-2">
      <form role="search" class="relative w-full sm:w-[280px]" @submit.prevent="submitQuery">
        <svg
          class="pointer-events-none absolute top-3.5 left-4 size-[18px] text-ink-muted"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          aria-hidden="true"
        >
          <circle cx="11" cy="11" r="7" />
          <path d="M20 20l-4-4" />
        </svg>
        <input
          v-model="q"
          class="ls-input rounded-pill! pl-11!"
          type="search"
          :maxlength="MAX_QUERY_LENGTH"
          placeholder="Tìm quán, món, khu vực…"
          aria-label="Tìm kiếm"
          :disabled="nearMode"
        />
      </form>

      <div class="flex flex-wrap items-center gap-2" role="group" aria-label="Danh mục">
        <button
          class="ls-fchip"
          type="button"
          :aria-pressed="state.category === null"
          @click="setCategory(null)"
        >
          Tất cả
        </button>
        <button
          v-for="root in roots"
          :key="root.id"
          class="ls-fchip"
          type="button"
          :aria-pressed="state.category === root.slug"
          @click="setCategory(state.category === root.slug ? null : root.slug)"
        >
          {{ root.name }}
        </button>
        <button
          v-if="childChip"
          class="ls-fchip"
          type="button"
          aria-pressed="true"
          @click="setCategory(null)"
        >
          {{ childChip.name }} ✕
        </button>
      </div>

      <span class="mx-1 h-6 w-px bg-line max-sm:hidden" aria-hidden="true"></span>

      <span class="ls-fchipwrap">
        <select
          class="ls-fchip"
          aria-label="Điểm tối thiểu"
          :data-active="state.minRating !== null"
          :value="state.minRating ?? ''"
          :disabled="nearMode"
          @change="onSelectNumber('minRating', $event)"
        >
          <option value="">Mọi mức điểm</option>
          <option v-for="r in MIN_RATING_OPTIONS" :key="r" :value="r">
            Điểm từ {{ r.toFixed(1) }}
          </option>
        </select>
      </span>
      <span class="ls-fchipwrap">
        <select
          class="ls-fchip"
          aria-label="Ngân sách"
          :data-active="state.price !== null"
          :value="state.price ?? ''"
          :disabled="nearMode"
          @change="onSelectNumber('price', $event)"
        >
          <option value="">Mọi mức giá</option>
          <option v-for="p in PRICE_OPTIONS" :key="p" :value="p">
            Dưới {{ formatNumber(p) }} ₫
          </option>
        </select>
      </span>
      <details class="relative" :class="nearMode && 'pointer-events-none'">
        <summary
          class="ls-fchip"
          :data-active="state.amenities.length > 0"
          :aria-disabled="nearMode || undefined"
          :tabindex="nearMode ? -1 : undefined"
          :class="nearMode && 'text-ink-muted! border-line!'"
        >
          Tiện ích<template v-if="state.amenities.length"> · {{ state.amenities.length }}</template>
          ▾
        </summary>
        <fieldset class="ls-pop absolute z-[500] mt-2 grid w-64 gap-2 p-4">
          <legend class="sr-only">Tiện ích — phải có đủ tất cả</legend>
          <label v-for="a in amenities.data.value ?? []" :key="a.id" class="ls-check">
            <input
              type="checkbox"
              :checked="state.amenities.includes(a.slug)"
              @change="toggleAmenity(a.slug)"
            />
            {{ a.name }}
          </label>
        </fieldset>
      </details>
      <button class="ls-fchip" type="button" :aria-pressed="nearMode" @click="toggleNear">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="3" />
          <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
        </svg>
        Quanh tôi<template v-if="nearMode"> · {{ formatDistance(state.radius) }}</template>
      </button>
      <span v-if="nearMode" class="ls-fchipwrap">
        <select
          class="ls-fchip"
          aria-label="Bán kính"
          :value="state.radius"
          @change="onSelectNumber('radius', $event)"
        >
          <option v-for="r in RADIUS_OPTIONS" :key="r" :value="r">
            Trong {{ formatDistance(r) }}
          </option>
        </select>
      </span>
      <button
        v-if="hasExtraFilters"
        class="ls-link cursor-pointer border-0 bg-transparent px-2"
        type="button"
        @click="clearFilters"
      >
        Xoá bộ lọc
      </button>
    </div>
    <p v-if="nearMode" class="-mt-3 mb-6 text-caption text-ink-muted">
      ● Tìm quanh vị trí chỉ lọc theo danh mục và bán kính — tắt "Quanh tôi" để tìm theo từ khóa,
      điểm, giá, tiện ích.
    </p>

    <div class="grid gap-8 lg:grid-cols-[1fr_540px]">
      <!-- Danh sách -->
      <div ref="list" class="order-2 lg:order-1">
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line pb-3">
          <h2 class="m-0 text-body font-bold" aria-live="polite">{{ heading }}</h2>
          <label v-if="sorts.length" class="flex items-center gap-2 text-ink-muted">
            Sắp xếp:
            <span class="ls-fchipwrap">
              <select class="ls-fchip h-8!" :value="currentSort ?? ''" @change="onSort">
                <option v-for="s in sorts" :key="s" :value="s">{{ SORT_LABELS[s] }}</option>
              </select>
            </span>
          </label>
          <span v-else class="text-caption text-ink-muted">Gần nhất trước</span>
        </div>

        <p v-if="results.degraded.value" class="mt-4 text-caption text-warning" role="status">
          ● Tìm kiếm đang chạy chế độ dự phòng: chỉ khớp theo tên địa điểm, kết quả có thể chưa đầy
          đủ.
        </p>

        <!-- Vị trí -->
        <div v-if="state.near === 'me' && locateStatus !== 'ready'" class="py-12" role="status">
          <template v-if="locateStatus === 'locating' || locateStatus === 'idle'">
            <p class="m-0 font-semibold">Đang lấy vị trí của bạn…</p>
            <p class="mt-1 text-ink-muted">
              Trình duyệt có thể hỏi quyền truy cập vị trí — chọn Cho phép.
            </p>
          </template>
          <template v-else>
            <p class="m-0 font-semibold">✕ Không lấy được vị trí của bạn</p>
            <p class="mt-1 text-ink-muted">
              {{
                locateStatus === 'denied'
                  ? 'Bạn đã chặn quyền vị trí cho trang này. Bật lại trong cài đặt trình duyệt, hoặc kéo bản đồ tới khu vực muốn tìm.'
                  : 'Thiết bị không xác định được vị trí lúc này. Thử lại, hoặc kéo bản đồ tới khu vực muốn tìm.'
              }}
            </p>
            <div class="mt-4 flex gap-2">
              <LsButton size="sm" @click="placeStore.locate()">Thử lại</LsButton>
              <LsButton size="sm" variant="ghost" @click="toggleNear">Tắt "Quanh tôi"</LsButton>
            </div>
          </template>
        </div>

        <template v-else>
          <div v-if="results.isPending.value" aria-hidden="true">
            <div
              v-for="i in 5"
              :key="i"
              class="grid grid-cols-[104px_1fr] gap-4 border-b border-line py-4 sm:grid-cols-[132px_1fr]"
            >
              <span class="ls-skel h-[100px]"></span>
              <span class="flex flex-col gap-2">
                <span class="ls-skel h-5 w-2/3"></span>
                <span class="ls-skel h-4 w-1/3"></span>
                <span class="ls-skel h-4 w-1/2"></span>
              </span>
            </div>
          </div>

          <div v-else-if="results.isError.value" class="py-12" role="alert">
            <p class="m-0 font-semibold">✕ Chưa tải được kết quả</p>
            <p class="mt-1 text-ink-muted">Kiểm tra kết nối mạng rồi thử lại.</p>
            <LsButton class="mt-4" size="sm" @click="results.refetch()">Thử lại</LsButton>
          </div>

          <div v-else-if="results.items.value.length === 0" class="py-12">
            <p class="m-0 font-semibold">Không tìm thấy địa điểm nào</p>
            <p class="mt-1 text-ink-muted">
              <template v-if="nearMode">Thử tăng bán kính hoặc chọn danh mục khác.</template>
              <template v-else-if="state.q">Thử từ khóa ngắn hơn, hoặc bỏ bớt bộ lọc.</template>
              <template v-else>Thử bỏ bớt bộ lọc.</template>
            </p>
            <div class="mt-4 flex flex-wrap gap-2">
              <LsButton v-if="hasExtraFilters" size="sm" variant="ghost" @click="clearFilters"
                >Xoá bộ lọc</LsButton
              >
              <LsButton v-if="$router.hasRoute('propose')" size="sm" :to="{ name: 'propose' }">
                Đề xuất địa điểm mới
              </LsButton>
            </div>
          </div>

          <template v-else>
            <PlaceRow
              v-for="place in results.items.value"
              :key="place.id"
              :data-place-id="place.id"
              :place="place"
              :active="place.id === activePlaceId"
              @hover="placeStore.setActive"
            />
            <div v-if="results.hasNextPage.value" ref="sentinel" class="flex justify-center py-6">
              <LsButton
                variant="ghost"
                :loading="results.isFetchingNextPage.value"
                @click="results.fetchNextPage()"
              >
                {{ results.isFetchingNextPage.value ? 'Đang tải…' : 'Xem thêm' }}
              </LsButton>
            </div>
            <p
              v-else-if="results.nearbyCapped.value"
              class="py-6 text-center text-caption text-ink-muted"
            >
              Hiện {{ results.items.value.length }} địa điểm gần nhất — thu nhỏ bán kính để xem kỹ
              hơn một khu.
            </p>
          </template>
        </template>
      </div>

      <!-- Bản đồ -->
      <div class="relative order-1 lg:order-2">
        <div class="sticky top-4">
          <PlaceMap
            class="h-[320px] rounded-lg lg:h-[calc(100vh-48px)] lg:max-h-[760px]"
            :places="results.items.value"
            :active-id="activePlaceId"
            :user-location="state.near === 'me' ? location : null"
            :fit-key="fitKey"
            @select="selectFromMap"
            @moved="movedView = $event"
          />
          <LsButton
            v-if="movedView"
            variant="sun"
            size="sm"
            class="absolute! top-4 left-1/2 z-[500] -translate-x-1/2"
            @click="searchThisArea"
          >
            Tìm trong vùng này
          </LsButton>
        </div>
      </div>
    </div>
  </div>
</template>
