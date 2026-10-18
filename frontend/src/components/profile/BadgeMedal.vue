<script setup lang="ts">
import { computed } from 'vue'

import type { Badge } from '@/types/api'
import { formatDate } from '@/utils/format'

/**
 * Huy chương một huy hiệu đã đạt (UC21, `.ls-medal`). `icon` là khóa trung tính từ backend (V4) — ánh xạ ở đây sang
 * hình + cặp màu của design system; khóa lạ dùng hình mặc định. Tên luôn hiện bằng chữ, hình chỉ trang trí.
 * Chỉ hiện huy hiệu đã đạt (supervisor chốt 2026-10-18 — API không trả tiến độ huy hiệu chưa đạt).
 */
const props = withDefaults(defineProps<{ badge: Badge; showDate?: boolean }>(), {
  showDate: false,
})

const STYLES: Record<string, { tone: 'sun' | 'coral' | 'olive'; path: string }> = {
  reviewer: { tone: 'sun', path: 'M4 5h16v11H9l-5 4z' },
  explorer: {
    tone: 'olive',
    path: 'M12 21s-7-6.2-7-11a7 7 0 0114 0c0 4.8-7 11-7 11zM12 12.5a2.5 2.5 0 100-5',
  },
  photographer: {
    tone: 'coral',
    path: 'M4 7h4l2-2h4l2 2h4v12H4zM12 16.5a3.5 3.5 0 100-7 3.5 3.5 0 000 7z',
  },
  helpful: {
    tone: 'olive',
    path: 'M7 11v9H4v-9zM7 11l4-8c1.7 0 3 1.3 3 3v3h5a2 2 0 0 1 2 2.3l-1.2 7A2 2 0 0 1 17.8 20H7',
  },
}
const FALLBACK = {
  tone: 'sun' as const,
  path: 'M12 15a6 6 0 100-12 6 6 0 000 12zM8 14l-2 7 6-3 6 3-2-7',
}

const style = computed(() => STYLES[props.badge.icon] ?? FALLBACK)
</script>

<template>
  <div :class="['ls-medal', `ls-medal--${style.tone}`]">
    <span class="m">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="style.path" /></svg>
    </span>
    {{ badge.name }}
    <small v-if="showDate">Đạt {{ formatDate(badge.awardedAt) }}</small>
  </div>
</template>
