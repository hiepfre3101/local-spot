<script setup lang="ts">
import { keepPreviousData, useQuery } from '@tanstack/vue-query'
import { computed, ref, useId } from 'vue'
import { RouterLink } from 'vue-router'

import { ownerApi } from '@/api/owner'
import { formatNumber } from '@/utils/format'
import { scoreText } from '@/utils/rating'
import { deltaText, shiftDate, splitPeriods, sum, vietnamToday } from '@/utils/stats'

import BarChart from '@/components/ui/BarChart.vue'
import OwnerReviewList from './OwnerReviewList.vue'

/**
 * Thống kê (UC26, FR-34): dải KPI + biểu đồ lượt xem theo ngày (cột `sea`) | cột "Cần phản hồi" tách bằng đường kẻ dọc
 * (`.ls-split`, P09 README). So với kỳ trước cùng độ dài: một lần gọi `stats` cho 2 kỳ rồi tách.
 *
 * Bản mẫu có "Lượt lưu" và "Đánh giá mới" theo kỳ — API thống kê không có hai số này, nên dải KPI dùng số có thật: lượt
 * xem, check-in (theo kỳ), điểm xếp hạng, tổng đánh giá (hiện trạng).
 */
const props = defineProps<{ placeId: number }>()

const RANGES = [7, 30, 90] as const
const uid = useId()
const days = ref<(typeof RANGES)[number]>(30)

const stats = useQuery({
  queryKey: computed(() => ['owner', 'stats', props.placeId, days.value]),
  queryFn: () => {
    const to = vietnamToday()
    return ownerApi.stats(props.placeId, shiftDate(to, -(2 * days.value - 1)), to)
  },
  placeholderData: keepPreviousData,
})

const views = computed(() => splitPeriods(stats.data.value?.viewsByDay ?? [], days.value))
const checkins = computed(() => splitPeriods(stats.data.value?.checkinsByDay ?? [], days.value))
const viewsDelta = computed(() => deltaText(sum(views.value.current), sum(views.value.previous)))
const checkinsDelta = computed(() =>
  deltaText(sum(checkins.value.current), sum(checkins.value.previous)),
)
const distribution = computed(() => {
  const d = stats.data.value?.ratingDistribution ?? {}
  const total = Object.values(d).reduce((a, b) => a + b, 0)
  return [5, 4, 3, 2, 1].map((star) => {
    const count = d[String(star)] ?? 0
    return { star, count, percent: total ? Math.round((count / total) * 100) : 0 }
  })
})

const trendClass = { up: 'ls-up', down: 'ls-down', flat: '' } as const
</script>

<template>
  <div>
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
      <p class="m-0 text-caption text-ink-muted">
        So với {{ days }} ngày liền trước · giờ Việt Nam
      </p>
      <div class="ls-field flex-row! items-center gap-2">
        <label :for="`${uid}-range`" class="m-0!">Khoảng</label>
        <span class="ls-selectwrap">
          <select :id="`${uid}-range`" v-model.number="days" class="ls-select">
            <option v-for="r in RANGES" :key="r" :value="r">{{ r }} ngày</option>
          </select>
        </span>
      </div>
    </div>

    <p v-if="stats.isError.value" role="alert">✕ Chưa tải được thống kê.</p>
    <template v-else-if="stats.data.value">
      <div class="ls-kpis mb-8">
        <div class="ls-kpi">
          <div class="ls-kpi__label">Lượt xem trang</div>
          <div class="ls-kpi__value">{{ formatNumber(sum(views.current)) }}</div>
          <div class="ls-kpi__delta" :class="trendClass[viewsDelta.trend]">
            {{ viewsDelta.text }}
          </div>
        </div>
        <div class="ls-kpi">
          <div class="ls-kpi__label">Check-in</div>
          <div class="ls-kpi__value">{{ formatNumber(sum(checkins.current)) }}</div>
          <div class="ls-kpi__delta" :class="trendClass[checkinsDelta.trend]">
            {{ checkinsDelta.text }}
          </div>
        </div>
        <div class="ls-kpi">
          <div class="ls-kpi__label">Điểm xếp hạng</div>
          <div class="ls-kpi__value">
            {{ stats.data.value.reviewCount ? scoreText(stats.data.value.bayesianScore) : '—' }}
          </div>
          <div class="ls-kpi__delta">Bayesian, như trang công khai</div>
        </div>
        <div class="ls-kpi">
          <div class="ls-kpi__label">Đánh giá</div>
          <div class="ls-kpi__value">{{ formatNumber(stats.data.value.reviewCount) }}</div>
          <div class="ls-kpi__delta">Đang hiển thị</div>
        </div>
      </div>

      <div class="grid gap-10 lg:grid-cols-[2fr_1fr] lg:gap-0 stats-split">
        <div class="min-w-0">
          <h2 class="ls-h2 text-title!">Lượt xem trang mỗi ngày</h2>
          <BarChart
            :series="views.current"
            :label="`Lượt xem trang mỗi ngày trong ${days} ngày qua`"
            unit="lượt xem"
          />
          <h2 class="ls-h2 mt-8 text-title!">Phân bố số sao</h2>
          <ul class="m-0 flex max-w-[420px] list-none flex-col gap-2 p-0">
            <li
              v-for="row in distribution"
              :key="row.star"
              class="grid grid-cols-[48px_1fr_56px] items-center gap-3"
            >
              <span>{{ row.star }} sao</span>
              <span class="h-2 bg-surface-sunk" aria-hidden="true"
                ><span class="block h-2 bg-sea" :style="{ width: `${row.percent}%` }"></span
              ></span>
              <span class="text-right text-caption text-ink-muted tabular-nums">{{
                formatNumber(row.count)
              }}</span>
            </li>
          </ul>
        </div>
        <div class="min-w-0">
          <div class="flex items-baseline justify-between gap-3">
            <h2 class="ls-h2 text-title!">Cần phản hồi</h2>
            <RouterLink class="ls-link text-caption" :to="{ query: { tab: 'reviews' } }"
              >Xem tất cả</RouterLink
            >
          </div>
          <OwnerReviewList :place-id="placeId" :limit="3" compact />
        </div>
      </div>
    </template>
    <div v-else aria-busy="true" aria-label="Đang tải thống kê">
      <span class="ls-skel h-24 w-full"></span>
    </div>
  </div>
</template>
