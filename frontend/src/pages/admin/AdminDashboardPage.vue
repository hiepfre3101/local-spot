<script setup lang="ts">
import { keepPreviousData, useQuery } from '@tanstack/vue-query'
import { computed, ref, useId } from 'vue'
import { RouterLink } from 'vue-router'

import { dashboardApi } from '@/api/dashboard'
import { moderationApi } from '@/api/moderation'
import { problemMessage, toProblem } from '@/api/problem'
import BarChart from '@/components/ui/BarChart.vue'
import LsAdminButton from '@/components/ui/LsAdminButton.vue'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, formatNumber } from '@/utils/format'
import { deltaText, shiftDate, vietnamToday } from '@/utils/stats'

import QueueTable from './moderation/QueueTable.vue'
import { MODERATION_PERMISSIONS, waitedText } from './moderation/moderation'

/**
 * AdminDashboard — Tổng quan (UC33, FR-41), trang đầu của quản trị viên (sitemap §2.3). Theo README thành phần: KPI là
 * **một dải** chia đường kẻ; biểu đồ | danh sách tách bằng đường kẻ dọc; hàng chờ FIFO đặt thẳng trong section.
 *
 * Số liệu theo `GET /admin/dashboard` (supervisor chốt 2026-10-17): so với kỳ liền trước cùng độ dài, khu vực / địa
 * điểm nhiều **đánh giá đang hiện mới viết**, việc tồn hiện tại. **Không có "Xuất CSV"** như bản mẫu (chốt cùng ngày:
 * API không xuất CSV). Báo cáo tăng là xấu → ▲ tô `danger` (ký hiệu + chữ vẫn nói đúng chiều).
 */
const RANGES = [7, 14, 30, 90] as const

const uid = useId()
const auth = useAuthStore()
const days = ref<(typeof RANGES)[number]>(14)

const dashboard = useQuery({
  queryKey: computed(() => ['admin', 'dashboard', days.value]),
  queryFn: () => {
    const to = vietnamToday()
    return dashboardApi.get(shiftDate(to, -(days.value - 1)), to)
  },
  placeholderData: keepPreviousData,
  staleTime: 0,
})
const data = computed(() => dashboard.data.value)
const errorText = computed(() =>
  dashboard.error.value ? problemMessage(toProblem(dashboard.error.value)) : null,
)

const subtitle = computed(() =>
  [`${days.value} ngày qua`, data.value && `cập nhật ${formatDateTime(data.value.generatedAt)}`]
    .filter(Boolean)
    .join(' · '),
)

const now = Date.now()
const canModerate = computed(() => auth.canAny(MODERATION_PERMISSIONS))
const queue = useQuery({
  queryKey: ['moderation', 'queue', null, 'dashboard'],
  queryFn: () => moderationApi.queue(null, null, 5),
  enabled: canModerate,
  staleTime: 0,
})

type Trend = 'up' | 'down' | 'flat'
/** `goodWhenUp = false` cho báo cáo: tăng là xấu. Ký hiệu ▲▼ + chữ luôn có — màu chỉ nhấn thêm. */
function trendClass(trend: Trend, goodWhenUp = true): string {
  if (trend === 'flat') return ''
  return (trend === 'up') === goodWhenUp ? 'ls-up' : 'ls-down'
}

const reviewsDelta = computed(() =>
  data.value ? deltaText(data.value.totals.reviews, data.value.previousTotals.reviews) : null,
)
const usersDelta = computed(() =>
  data.value ? deltaText(data.value.totals.newUsers, data.value.previousTotals.newUsers) : null,
)
const reportsDelta = computed(() =>
  data.value ? deltaText(data.value.totals.reports, data.value.previousTotals.reports) : null,
)

/** Việc chờ duyệt (địa điểm, đánh giá, sở hữu) — báo cáo có ô riêng. */
const pending = computed(() => {
  const p = data.value?.pendingCounts
  if (!p) return null
  return {
    total: p.places + p.reviews + p.claims,
    detail: [
      `${formatNumber(p.places)} địa điểm`,
      `${formatNumber(p.reviews)} đánh giá`,
      `${formatNumber(p.claims)} sở hữu`,
    ].join(' · '),
    oldest: p.oldestSubmittedAt ? `cũ nhất ${waitedText(p.oldestSubmittedAt, now)}` : null,
  }
})
</script>

<template>
  <div>
    <div class="ls-pagehead flex-wrap">
      <div>
        <h1>Tổng quan</h1>
        <p>{{ subtitle }}</p>
      </div>
      <div>
        <label class="sr-only" :for="`${uid}-range`">Khoảng thời gian</label>
        <span class="ls-selectwrap w-[160px]">
          <select :id="`${uid}-range`" v-model.number="days" class="ls-select">
            <option v-for="r in RANGES" :key="r" :value="r">{{ r }} ngày qua</option>
          </select>
        </span>
      </div>
    </div>

    <p v-if="errorText" role="alert" class="ls-field__error mt-6">
      ✕ {{ errorText }}
      <LsAdminButton size="sm" class="ml-2" @click="dashboard.refetch()">Thử lại</LsAdminButton>
    </p>

    <div v-else-if="!data" aria-busy="true" aria-label="Đang tải số liệu" class="py-8">
      <span class="ls-skel h-24 w-full"></span>
    </div>

    <template v-else>
      <!-- Dải KPI -->
      <section class="ls-section" :aria-busy="dashboard.isFetching.value">
        <h2 class="sr-only">Chỉ số chính</h2>
        <div class="ls-kpis">
          <div class="ls-kpi">
            <div class="ls-kpi__label">Đánh giá mới</div>
            <div class="ls-kpi__value">{{ formatNumber(data.totals.reviews) }}</div>
            <div class="ls-kpi__delta" :class="trendClass(reviewsDelta!.trend)">
              {{ reviewsDelta!.text }} so với kỳ trước
            </div>
          </div>
          <div class="ls-kpi">
            <div class="ls-kpi__label">Hàng chờ kiểm duyệt</div>
            <div class="ls-kpi__value">{{ formatNumber(pending!.total) }}</div>
            <div class="ls-kpi__delta">
              {{ pending!.detail
              }}<template v-if="pending!.oldest"> · {{ pending!.oldest }}</template>
            </div>
          </div>
          <div class="ls-kpi">
            <div class="ls-kpi__label">Báo cáo nhận được</div>
            <div class="ls-kpi__value">{{ formatNumber(data.totals.reports) }}</div>
            <div class="ls-kpi__delta" :class="trendClass(reportsDelta!.trend, false)">
              {{ reportsDelta!.text }} · {{ formatNumber(data.pendingCounts.reports) }} chưa xử lý
            </div>
          </div>
          <div class="ls-kpi">
            <div class="ls-kpi__label">Thành viên mới</div>
            <div class="ls-kpi__value">{{ formatNumber(data.totals.newUsers) }}</div>
            <div class="ls-kpi__delta" :class="trendClass(usersDelta!.trend)">
              {{ usersDelta!.text }} so với kỳ trước
            </div>
          </div>
        </div>
      </section>

      <!-- Đánh giá theo ngày | khu vực -->
      <section class="ls-section grid gap-8 lg:grid-cols-[2fr_1fr] lg:gap-0 stats-split">
        <div class="min-w-0">
          <div class="ls-section__head">
            <h2>Đánh giá mới mỗi ngày</h2>
            <span class="text-caption text-ink-muted">Di chuột lên cột để xem số</span>
          </div>
          <BarChart
            :series="data.reviewsByDay"
            :label="`Đánh giá được gửi mỗi ngày trong ${days} ngày qua`"
            unit="đánh giá"
          />
        </div>
        <div class="min-w-0">
          <div class="ls-section__head"><h2>Khu vực nhiều đánh giá</h2></div>
          <p v-if="!data.reviewsByCity.length" class="m-0 text-ink-muted">
            Chưa có đánh giá nào trong khoảng này.
          </p>
          <ol v-else class="rank">
            <li v-for="c in data.reviewsByCity" :key="c.city">
              <span>{{ c.city }}</span>
              <span>{{ formatNumber(c.reviewCount) }}</span>
            </li>
          </ol>
        </div>
      </section>

      <!-- Thành viên mới | địa điểm nổi bật -->
      <section class="ls-section grid gap-8 lg:grid-cols-[2fr_1fr] lg:gap-0 stats-split">
        <div class="min-w-0">
          <div class="ls-section__head"><h2>Thành viên mới mỗi ngày</h2></div>
          <BarChart
            :series="data.newUsersByDay"
            :label="`Tài khoản mới mỗi ngày trong ${days} ngày qua`"
            unit="tài khoản"
          />
        </div>
        <div class="min-w-0">
          <div class="ls-section__head"><h2>Địa điểm nổi bật</h2></div>
          <p v-if="!data.hotPlaces.length" class="m-0 text-ink-muted">
            Chưa có địa điểm nào có đánh giá mới.
          </p>
          <ol v-else class="rank">
            <li v-for="h in data.hotPlaces" :key="h.place.id">
              <RouterLink
                class="ls-link min-w-0 truncate"
                :to="{ name: 'place', params: { slug: h.place.slug } }"
                >{{ h.place.name }}</RouterLink
              >
              <span :aria-label="`${h.newReviewCount} đánh giá mới`">{{
                formatNumber(h.newReviewCount)
              }}</span>
            </li>
          </ol>
        </div>
      </section>

      <!-- Hàng chờ (vài mục đầu) -->
      <section v-if="canModerate" class="ls-section">
        <div class="ls-section__head">
          <h2>
            Hàng chờ kiểm duyệt
            <span class="text-caption font-normal text-ink-muted">· cũ nhất trước (FIFO)</span>
          </h2>
          <RouterLink class="ls-link text-caption" :to="{ name: 'admin-queue' }"
            >Xem tất cả →</RouterLink
          >
        </div>
        <QueueTable
          :rows="queue.data.value?.items ?? []"
          :loading="queue.isPending.value"
          :failed="queue.isError.value"
          :now="now"
        />
        <p v-if="queue.isError.value" role="alert" class="ls-field__error mt-2">
          ✕ Chưa tải được hàng chờ.
        </p>
      </section>
    </template>
  </div>
</template>

<style scoped>
/* Danh sách xếp hạng (bản mẫu AdminDashboard `.rank`) */
.rank {
  list-style: none;
  margin: 0;
  padding: 0;
}
.rank li {
  display: flex;
  justify-content: space-between;
  gap: var(--space-4);
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  font: 400 14px/20px var(--font-sans);
}
.rank li:last-child {
  border-bottom: 0;
}
.rank li > span:last-child {
  flex: none;
  font-variant-numeric: tabular-nums;
  color: var(--ink-muted);
}
</style>
