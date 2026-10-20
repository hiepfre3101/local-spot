<script setup lang="ts">
import { keepPreviousData, useQuery } from '@tanstack/vue-query'
import { computed, ref, useId, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { catalogApi } from '@/api/catalog'
import { usersApi } from '@/api/users'
import LsAvatar from '@/components/ui/LsAvatar.vue'
import LsButton from '@/components/ui/LsButton.vue'
import { useAuthStore } from '@/stores/auth'
import type { LeaderboardEntry, LeaderboardPeriod } from '@/types/api'
import { formatNumber } from '@/utils/format'

/**
 * P08 · Bảng xếp hạng (UC21, FR-29). Bộ lọc trên URL (sitemap): `period` = WEEK / MONTH / ALL (mặc định MONTH như API),
 * `city` = khu vực (`GET /cities` — thêm 2026-10-19). Ba người đầu là bục 3 ô cạnh nhau (hạng 1 nền `sun`, 2–3 nền
 * `surface-sunk`, không khung lồng), còn lại là danh sách chia đường kẻ.
 *
 * Supervisor chốt 2026-10-19: hàng của chính mình **chỉ ghim khi nằm trong top 100** (API trả top N) — ngoài top thì
 * nói rõ; **không có nút Theo dõi** trên từng hàng (API không có trạng thái theo dõi) — tên dẫn tới trang cá nhân.
 * Bản mẫu có khu vực dưới tên — người dùng không có thành phố trong dữ liệu nên bỏ.
 */
const LIMIT = 100
const PAGE = 20
const PERIODS: { value: LeaderboardPeriod; label: string; hint: string }[] = [
  { value: 'WEEK', label: '7 ngày', hint: '7 ngày qua' },
  { value: 'MONTH', label: '30 ngày', hint: '30 ngày qua' },
  { value: 'ALL', label: 'Tất cả', hint: 'từ trước tới nay' },
]

const uid = useId()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const period = computed<LeaderboardPeriod>(() => {
  const p = route.query.period
  return PERIODS.some((x) => x.value === p) ? (p as LeaderboardPeriod) : 'MONTH'
})
const city = computed(() =>
  typeof route.query.city === 'string' && route.query.city ? route.query.city : null,
)

function setFilter(next: { period?: LeaderboardPeriod; city?: string | null }): void {
  const p = next.period ?? period.value
  const c = next.city === undefined ? city.value : next.city
  void router.replace({
    query: { period: p === 'MONTH' ? undefined : p, city: c ?? undefined },
  })
}

const cities = useQuery({
  queryKey: ['catalog', 'cities'],
  queryFn: catalogApi.cities,
  staleTime: 10 * 60_000,
})

const board = useQuery({
  queryKey: computed(() => ['leaderboard', period.value, city.value]),
  queryFn: () => usersApi.leaderboard(period.value, city.value, LIMIT),
  placeholderData: keepPreviousData,
})
const entries = computed<LeaderboardEntry[]>(() => board.data.value ?? [])
const podium = computed(() => entries.value.slice(0, 3))

const shown = ref(PAGE)
watch([period, city], () => (shown.value = PAGE))
const rest = computed(() => entries.value.slice(3, Math.max(3, shown.value)))

/** Hàng của tôi (khi có trong top 100) + số điểm còn thiếu để bằng hạng ngay trên (cùng điểm = cùng hạng). */
const me = computed(() => {
  const id = auth.user?.id
  if (id === undefined) return null
  const index = entries.value.findIndex((e) => e.user.id === id)
  if (index < 0) return { entry: null, gap: null }
  const entry = entries.value[index]!
  const above = entries.value
    .slice(0, index)
    .reverse()
    .find((e) => e.points > entry.points)
  return { entry, gap: above ? { points: above.points - entry.points, rank: above.rank } : null }
})

const periodHint = computed(() => PERIODS.find((p) => p.value === period.value)!.hint)
const isMe = (entry: LeaderboardEntry) => entry.user.id === auth.user?.id
</script>

<template>
  <article class="ls-pub py-8">
    <div class="mb-8 flex flex-wrap items-end justify-between gap-6">
      <div>
        <p class="ls-crumb m-0">Cộng đồng</p>
        <h1 class="ls-h1 m-0 uppercase">Bảng xếp hạng</h1>
        <p class="mt-2 mb-0 text-ink-muted">
          Điểm đóng góp {{ periodHint }}<template v-if="city"> · {{ city }}</template>
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <div class="flex flex-wrap gap-2" role="group" aria-label="Khoảng thời gian">
          <button
            v-for="p in PERIODS"
            :key="p.value"
            type="button"
            class="ls-fchip"
            :aria-pressed="period === p.value"
            @click="setFilter({ period: p.value })"
          >
            {{ p.label }}
          </button>
        </div>
        <label class="sr-only" :for="`${uid}-city`">Khu vực</label>
        <span class="ls-selectwrap w-[180px]">
          <select
            :id="`${uid}-city`"
            class="ls-select rounded-pill!"
            :value="city ?? ''"
            @change="setFilter({ city: ($event.target as HTMLSelectElement).value || null })"
          >
            <option value="">Toàn quốc</option>
            <option v-if="city && !cities.data.value?.some((c) => c.name === city)" :value="city">
              {{ city }}
            </option>
            <option v-for="c in cities.data.value ?? []" :key="c.name" :value="c.name">
              {{ c.name }}
            </option>
          </select>
        </span>
      </div>
    </div>

    <section v-if="board.isError.value" role="alert" class="py-8">
      <p class="m-0">✕ Chưa tải được bảng xếp hạng.</p>
      <LsButton class="mt-4" variant="ghost" @click="board.refetch()">Thử lại</LsButton>
    </section>

    <div v-else-if="board.isPending.value" aria-busy="true" aria-label="Đang tải bảng xếp hạng">
      <div class="mb-6 grid gap-4 sm:grid-cols-3">
        <span v-for="i in 3" :key="i" class="ls-skel h-[220px] rounded-md"></span>
      </div>
      <span class="ls-skel h-40 w-full"></span>
    </div>

    <p v-else-if="!entries.length" class="py-8 text-ink-muted">
      Chưa có ai có điểm {{ periodHint }}<template v-if="city"> ở {{ city }}</template
      >. Viết đánh giá, check-in hay đề xuất địa điểm để đứng đầu bảng!
    </p>

    <template v-else>
      <div :aria-busy="board.isFetching.value">
        <!-- Bục 3 ô -->
        <ol class="podium m-0 mb-6 grid list-none gap-4 p-0 sm:grid-cols-3">
          <li
            v-for="(e, i) in podium"
            :key="e.user.id"
            :class="['pod', i === 0 ? 'pod--first' : 'pod--rest']"
            :aria-current="isMe(e) ? 'true' : undefined"
          >
            <span class="pod__rank" :aria-label="`Hạng ${e.rank}`">{{ e.rank }}</span>
            <LsAvatar
              :name="e.user.displayName"
              :src="e.user.avatarUrl"
              class="h-[72px]! w-[72px]! text-[22px]!"
            />
            <RouterLink class="pod__name" :to="{ name: 'profile', params: { id: e.user.id } }">{{
              e.user.displayName
            }}</RouterLink>
            <span class="pod__pts"
              >{{ formatNumber(e.points) }} điểm<template v-if="isMe(e)"> · Bạn</template></span
            >
          </li>
        </ol>

        <!-- Phần còn lại -->
        <ol v-if="rest.length" class="lb m-0 list-none p-0" :start="4">
          <li
            v-for="e in rest"
            :key="e.user.id"
            :class="{ 'lb--me': isMe(e) }"
            :aria-current="isMe(e) ? 'true' : undefined"
          >
            <span class="lb__rank" :aria-label="`Hạng ${e.rank}`">{{ e.rank }}</span>
            <LsAvatar :name="e.user.displayName" :src="e.user.avatarUrl" size="sm" />
            <RouterLink
              class="lb__name min-w-0 truncate"
              :to="{ name: 'profile', params: { id: e.user.id } }"
              >{{ e.user.displayName }}<template v-if="isMe(e)"> (Bạn)</template></RouterLink
            >
            <span class="lb__pts">{{ formatNumber(e.points) }}</span>
          </li>
        </ol>
        <div v-if="entries.length > shown" class="pt-6">
          <LsButton variant="ghost" @click="shown += PAGE">Xem thêm</LsButton>
        </div>

        <!-- Hàng của tôi -->
        <div v-if="me" class="lb lb--pinned mt-6" aria-live="polite">
          <template v-if="me.entry">
            <!-- Luôn ghim ở cuối (P08), kể cả khi hàng đã nằm trong danh sách phía trên -->
            <div class="lb__row lb--me">
              <span class="lb__rank" :aria-label="`Hạng ${me.entry.rank}`">{{
                me.entry.rank
              }}</span>
              <LsAvatar :name="auth.user!.displayName" :src="auth.user!.avatarUrl" size="sm" />
              <span class="flex min-w-0 flex-col">
                <b>Bạn</b>
                <small class="text-ink-muted">
                  {{
                    me.gap
                      ? `Còn ${formatNumber(me.gap.points)} điểm để lên hạng ${me.gap.rank}`
                      : 'Bạn đang dẫn đầu bảng!'
                  }}
                </small>
              </span>
              <span class="lb__pts">{{ formatNumber(me.entry.points) }}</span>
            </div>
          </template>
          <p v-else class="m-0 py-3 text-ink-muted">
            Bạn chưa vào top {{ LIMIT }}<template v-if="city"> ở {{ city }}</template> — viết đánh
            giá, check-in để có điểm.
          </p>
        </div>
      </div>
    </template>

    <p class="mt-6 text-caption text-ink-muted">
      Điểm chỉ tính nội dung đang hiển thị: đánh giá × 10 · ảnh trong đánh giá × 2 (tối đa 5 ảnh) ·
      lượt hữu ích nhận được × 1 · check-in × 1 · đề xuất được duyệt × 15. Cùng điểm cùng hạng.
      <RouterLink
        v-if="auth.user"
        class="ls-link"
        :to="{ name: 'profile', params: { id: auth.user.id }, query: { tab: 'badges' } }"
        >Xem huy hiệu của bạn</RouterLink
      >
    </p>
  </article>
</template>

<style scoped>
/* Bục 3 ô (bản mẫu P08): hạng 1 nền sun, 2–3 nền surface-sunk — cạnh nhau, không khung lồng */
.pod {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: 28px var(--space-4);
  border-radius: var(--radius-md);
  font: 400 14px/20px var(--font-sans);
  text-align: center;
}
.pod--first {
  background: var(--sun);
  color: var(--on-accent);
}
.pod--rest {
  background: var(--surface-sunk);
}
.pod__rank {
  font: 800 40px/1 var(--font-sans);
}
.pod__name {
  font: 700 18px/24px var(--font-sans);
  color: inherit;
  text-decoration: none;
  overflow-wrap: anywhere;
}
.pod__name:hover,
.pod__name:focus-visible {
  text-decoration: underline;
}
.pod--rest .pod__pts {
  color: var(--ink-muted);
}
.pod[aria-current] .pod__pts {
  font-weight: 700;
}

/* Danh sách chia đường kẻ */
.lb {
  border-top: 1px solid var(--line);
}
.lb > li,
.lb__row {
  display: grid;
  grid-template-columns: 48px 44px 1fr auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--line);
  font: 400 14px/20px var(--font-sans);
}
.lb__rank {
  font: 700 16px/1 var(--font-sans);
  color: var(--ink-muted);
  text-align: center;
}
.lb__name {
  font-weight: 600;
  color: var(--ink);
  text-decoration: none;
}
.lb__name:hover,
.lb__name:focus-visible {
  text-decoration: underline;
}
.lb__pts {
  font: 700 16px/1 var(--font-sans);
  font-variant-numeric: tabular-nums;
  text-align: right;
}
/* Hàng của chính mình: nền sunk + chữ "Bạn" (không chỉ bằng màu) */
.lb--me {
  background: var(--surface-sunk);
}
.lb--pinned {
  border-top: 0;
}
.lb--pinned .lb__row {
  border-bottom: 0;
}
</style>
