<script setup lang="ts">
import { RouterLink, RouterView, useRoute } from 'vue-router'

import FlatPicture from '@/components/ui/FlatPicture.vue'

/**
 * Khung trang tài khoản (P01): ảnh bo `radius-lg` bên trái, form 440px bên phải; màn hẹp chỉ còn form. Lớp `ls-pub`
 * đổi control sang kiểu công khai (ô 46px, bo 12px, nhãn chữ thường).
 */
const route = useRoute()
</script>

<template>
  <div class="ls-pub mx-auto min-h-screen max-w-[1200px] px-4 pb-12 md:px-12">
    <header class="ls-nav mb-8">
      <RouterLink class="ls-nav__logo" :to="{ name: 'home' }">LocalSpot</RouterLink>
      <span v-if="route.name === 'login'" class="text-caption text-ink-muted">
        Chưa có tài khoản?
        <RouterLink class="ls-link" :to="{ name: 'register' }">Đăng ký</RouterLink>
      </span>
      <span v-else-if="route.name === 'register'" class="text-caption text-ink-muted">
        Đã có tài khoản? <RouterLink class="ls-link" :to="{ name: 'login' }">Đăng nhập</RouterLink>
      </span>
    </header>
    <main class="grid items-center gap-16 lg:grid-cols-[1fr_440px]">
      <FlatPicture class="h-[560px] rounded-lg max-lg:hidden">
        <p class="auth-quote">“Mỗi góc phố đều đáng được kể.”</p>
      </FlatPicture>
      <div class="w-full max-w-[440px] justify-self-center">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<style scoped>
/* Câu trích trên ảnh (P01): chữ 800 trên nền ảnh, màu on-accent cố định ở cả hai theme */
.auth-quote {
  position: absolute;
  left: var(--space-8);
  bottom: var(--space-6);
  margin: 0;
  max-width: 360px;
  font: 800 30px/1.05 var(--font-sans);
  color: var(--on-accent);
}
</style>
