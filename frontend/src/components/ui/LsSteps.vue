<script setup lang="ts">
/** Thanh bước (P01 khôi phục mật khẩu): bước hiện tại = vạch `ink` + chữ đậm, không chỉ bằng màu. */
defineProps<{ steps: readonly string[]; current: number }>()
</script>

<template>
  <ol class="ls-steps" aria-label="Các bước">
    <li
      v-for="(step, index) in steps"
      :key="step"
      :aria-current="index === current ? 'step' : undefined"
    >
      {{ step }}
    </li>
  </ol>
</template>
