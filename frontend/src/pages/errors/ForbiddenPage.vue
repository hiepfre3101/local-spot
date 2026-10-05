<script setup lang="ts">
import LsButton from '@/components/ui/LsButton.vue'
</script>

<template>
  <section class="flex flex-col items-start gap-4 py-16">
    <span class="ls-kick">Lỗi 403</span>
    <h1 class="ls-h1">Bạn không có quyền xem trang này</h1>
    <p class="m-0 max-w-[56ch] text-ink-muted">
      Trang này dành cho vai trò khác. Nếu bạn cho rằng mình cần quyền này, hãy liên hệ quản trị
      viên.
    </p>
    <LsButton :to="{ name: 'home' }">Về trang chủ</LsButton>
  </section>
</template>
