<script setup lang="ts">
/** Lỗi chung của form (không gắn được vào một trường) — đọc ngay bằng trình đọc màn hình nhờ `role="alert"`. */
defineProps<{ message: string | null }>()
</script>

<template>
  <p v-if="message" role="alert" class="ls-field__error m-0">✕ {{ message }}</p>
</template>
