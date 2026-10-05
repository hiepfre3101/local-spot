<script setup lang="ts">
import LsButton from '@/components/ui/LsButton.vue'
</script>

<template>
  <section class="flex flex-col items-start gap-4 py-16">
    <span class="ls-kick">Lỗi 404</span>
    <h1 class="ls-h1">Không tìm thấy trang này</h1>
    <p class="m-0 max-w-[56ch] text-ink-muted">
      Có thể liên kết đã cũ hoặc địa điểm đã bị gỡ. Thử quay về trang chủ để tìm tiếp.
    </p>
    <LsButton :to="{ name: 'home' }">Về trang chủ</LsButton>
  </section>
</template>
