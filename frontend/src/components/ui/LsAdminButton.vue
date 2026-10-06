<script setup lang="ts">
/**
 * Nút quản trị (vuông 2px) — Button README: một `primary` mỗi trang; `danger` cho Từ chối / Xoá / Khoá tài khoản (luôn
 * có chữ). Không bao giờ dùng nút pill trong quản trị.
 */
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'danger'
    size?: 'sm' | 'md'
    type?: 'button' | 'submit'
    loading?: boolean
    disabled?: boolean
  }>(),
  { variant: 'secondary', size: 'md', type: 'button' },
)
</script>

<template>
  <button
    :type="type"
    :class="['ls-abtn', `ls-abtn--${variant}`, size === 'sm' && 'ls-abtn--sm']"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
  >
    <slot />
  </button>
</template>
