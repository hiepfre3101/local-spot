<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

/**
 * Khung "Cài đặt tài khoản" (P06): menu trái Hồ sơ / Mật khẩu / Thông báo, mục đang mở có "→" + chữ đậm; form bên
 * phải. Chỉ hiện mục đã có route (Hồ sơ, Thông báo làm cùng module tương ứng).
 */
defineProps<{ title: string }>()

const SECTIONS = [
  { label: 'Hồ sơ', routeName: 'settings-profile' },
  { label: 'Mật khẩu', routeName: 'settings-password' },
  { label: 'Thông báo', routeName: 'settings-notifications' },
] as const

const router = useRouter()
const route = useRoute()
const sections = computed(() => SECTIONS.filter((s) => router.hasRoute(s.routeName)))
</script>

<template>
  <div class="grid gap-16 md:grid-cols-[260px_1fr]">
    <div>
      <span class="ls-kick">Tài khoản</span>
      <h1 class="ls-h2 mt-2">Cài đặt tài khoản</h1>
      <nav class="snav" aria-label="Cài đặt">
        <RouterLink
          v-for="section in sections"
          :key="section.routeName"
          :to="{ name: section.routeName }"
          :aria-current="route.name === section.routeName ? 'page' : undefined"
        >
          {{ section.label }}
        </RouterLink>
      </nav>
    </div>
    <section>
      <h2 class="ls-h2">{{ title }}</h2>
      <slot />
    </section>
  </div>
</template>

<style scoped>
.snav {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--line);
}
.snav a {
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--line);
  color: var(--ink);
  text-decoration: none;
  font: 500 15px/20px var(--font-sans);
}
.snav a[aria-current] {
  font-weight: 700;
}
.snav a[aria-current]::before {
  content: '→ ';
}
</style>
