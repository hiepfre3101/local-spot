<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

/**
 * Nút công khai (pill) — Button README: `primary` olive cho hành động chính mỗi khối, `accent` coral cho CTA duy nhất
 * trên băng cam / hero, `ghost` cho Đăng nhập. Có `to` thì là liên kết router.
 */
const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'accent' | 'sun' | 'ghost'
    size?: 'sm' | 'md' | 'lg'
    to?: RouteLocationRaw
    type?: 'button' | 'submit'
    loading?: boolean
    disabled?: boolean
    block?: boolean
  }>(),
  { variant: 'primary', size: 'md', to: undefined, type: 'button' },
)

const classes = computed(() => [
  'ls-btn',
  `ls-btn--${props.variant}`,
  props.size !== 'md' && `ls-btn--${props.size}`,
  props.block && 'w-full',
])
</script>

<template>
  <RouterLink v-if="to" :to="to" :class="classes">
    <slot />
  </RouterLink>
  <button
    v-else
    :type="type"
    :class="classes"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
  >
    <slot />
  </button>
</template>
