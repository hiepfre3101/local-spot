<script setup lang="ts">
import { computed, useId } from 'vue'

import type { DailyCount } from '@/types/api'
import { formatNumber } from '@/utils/format'

/**
 * Biểu đồ cột một chuỗi theo ngày (P09 / AdminDashboard: cột `sea`, rê chuột đổi `olive`, lưới `line`) — SVG tự vẽ,
 * không thêm thư viện biểu đồ ngoài stack. Trình đọc màn hình đọc bảng số liệu ẩn thay cho hình.
 */
const props = defineProps<{ series: DailyCount[]; label: string; unit: string }>()

const id = useId()
const WIDTH = 640
const HEIGHT = 200
const PAD_LEFT = 36
const PAD_BOTTOM = 22

const max = computed(() => Math.max(1, ...props.series.map((d) => d.count)))
/** Trục tung tròn số: 0 · nửa · đỉnh. */
const ticks = computed(() => {
  const top = niceCeil(max.value)
  return [0, top / 2, top]
})
const top = computed(() => ticks.value[2]!)
const slot = computed(() => (WIDTH - PAD_LEFT) / Math.max(1, props.series.length))
const barWidth = computed(() => Math.max(2, slot.value * 0.66))
/** Nhãn ngày thưa dần theo độ dài chuỗi để không chồng chữ. */
const labelEvery = computed(() => Math.ceil(props.series.length / 8))

function y(value: number): number {
  return HEIGHT - PAD_BOTTOM - (value / top.value) * (HEIGHT - PAD_BOTTOM - 8)
}

function shortDate(iso: string): string {
  const [, m, d] = iso.split('-')
  return `${d}/${m}`
}

function niceCeil(value: number): number {
  const magnitude = 10 ** Math.floor(Math.log10(value))
  const step = [1, 2, 5, 10].find((s) => s * magnitude >= value)! * magnitude
  return Math.max(2, step)
}
</script>

<template>
  <figure class="m-0">
    <svg
      class="chart"
      :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
      role="img"
      :aria-labelledby="`${id}-caption`"
    >
      <g v-for="t in ticks" :key="t">
        <line :x1="PAD_LEFT" :x2="WIDTH" :y1="y(t)" :y2="y(t)" />
        <text :x="PAD_LEFT - 6" :y="y(t) + 4" text-anchor="end">{{ formatNumber(t) }}</text>
      </g>
      <g v-for="(day, i) in series" :key="day.date" class="bar">
        <rect
          :x="PAD_LEFT + i * slot + (slot - barWidth) / 2"
          :y="y(day.count)"
          :width="barWidth"
          :height="Math.max(0, HEIGHT - PAD_BOTTOM - y(day.count))"
        />
        <rect
          class="hit"
          :x="PAD_LEFT + i * slot"
          y="0"
          :width="slot"
          :height="HEIGHT - PAD_BOTTOM"
        >
          <title>{{ shortDate(day.date) }}: {{ formatNumber(day.count) }} {{ unit }}</title>
        </rect>
        <text
          v-if="i % labelEvery === 0"
          :x="PAD_LEFT + i * slot + slot / 2"
          :y="HEIGHT - 6"
          text-anchor="middle"
        >
          {{ shortDate(day.date) }}
        </text>
      </g>
    </svg>
    <figcaption :id="`${id}-caption`" class="sr-only">{{ label }}</figcaption>
    <table class="sr-only">
      <caption>
        {{
          label
        }}
      </caption>
      <thead>
        <tr>
          <th scope="col">Ngày</th>
          <th scope="col">{{ unit }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="day in series" :key="day.date">
          <td>{{ shortDate(day.date) }}</td>
          <td>{{ day.count }}</td>
        </tr>
      </tbody>
    </table>
  </figure>
</template>

<style scoped>
.chart {
  display: block;
  width: 100%;
  height: auto;
  font: 400 11px var(--font-sans);
}
.chart line {
  stroke: var(--line);
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}
.chart text {
  fill: var(--ink-muted);
}
.bar rect:first-child {
  fill: var(--sea);
}
.bar:hover rect:first-child {
  fill: var(--olive);
}
.hit {
  fill: transparent;
}
</style>
