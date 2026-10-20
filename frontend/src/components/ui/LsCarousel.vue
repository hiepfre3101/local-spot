<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useTemplateRef } from 'vue'

/**
 * Carousel công khai (P03): tiêu đề + mô tả trái; bộ đếm "1–2 / 12" và hai nút tròn ‹ › phải (‹ vô hiệu ở đầu, › ở
 * cuối). Màn cảm ứng vuốt ngang có snap. **Không tự chạy.** Số mục mỗi lượt qua `per` (CSS `--per`; màn hẹp CSS ép
 * 1–2 mục). Vị trí tính từ cuộn thật nên đúng cả khi người dùng vuốt.
 */
const props = withDefaults(
  defineProps<{
    title: string
    subtitle?: string
    count: number
    per?: number
    headingLevel?: 'h2' | 'h3'
  }>(),
  { subtitle: undefined, per: 3, headingLevel: 'h2' },
)

const track = useTemplateRef<HTMLDivElement>('track')
const first = ref(0)
const visible = ref(props.per)

function measure(): void {
  const el = track.value
  const item = el?.firstElementChild as HTMLElement | null
  if (!el || !item) return
  const gap = parseFloat(getComputedStyle(el).columnGap) || 0
  const step = item.offsetWidth + gap
  if (step <= 0) return
  first.value = Math.round(el.scrollLeft / step)
  visible.value = Math.max(1, Math.floor((el.clientWidth + gap) / step))
}

function page(direction: 1 | -1): void {
  const el = track.value
  if (!el) return
  el.scrollBy({ left: direction * el.clientWidth, behavior: 'smooth' })
}

const atStart = computed(() => first.value <= 0)
const atEnd = computed(() => first.value + visible.value >= props.count)
const range = computed(() => {
  const from = Math.min(first.value + 1, props.count)
  const to = Math.min(first.value + visible.value, props.count)
  return from === to ? `${from} / ${props.count}` : `${from}–${to} / ${props.count}`
})

let observer: ResizeObserver | null = null
onMounted(() => {
  measure()
  if (track.value && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(measure)
    observer.observe(track.value)
  }
})
onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <section class="ls-car" :style="{ '--per': per }" :aria-label="title">
    <div class="ls-car__head">
      <div>
        <component :is="headingLevel">{{ title }}</component>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="count > visible" class="ls-car__ctl">
        <span aria-live="polite">{{ range }}</span>
        <button
          class="ls-car__btn"
          type="button"
          aria-label="Trước"
          :disabled="atStart"
          @click="page(-1)"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7" /></svg>
        </button>
        <button
          class="ls-car__btn"
          type="button"
          aria-label="Sau"
          :disabled="atEnd"
          @click="page(1)"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5l7 7-7 7" /></svg>
        </button>
      </div>
    </div>
    <div ref="track" class="ls-car__track" @scroll.passive="measure">
      <slot />
    </div>
  </section>
</template>
