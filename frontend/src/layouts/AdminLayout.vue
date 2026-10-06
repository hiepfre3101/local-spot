<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import { ROLE_LABELS, primaryRole } from '@/auth/permissions'
import ThemeToggle from '@/components/layout/ThemeToggle.vue'
import { useAuthStore } from '@/stores/auth'

import { visibleAdminNav, type AdminNavItem } from './adminNav'

/**
 * Khung quản trị (AdminDashboard README): sidebar 224px theo vai trò — Tổng quan (quản trị viên) · Kiểm duyệt ·
 * Quản trị; tên và vai trò người đăng nhập ở chân sidebar. Bo góc tối thiểu, không bóng.
 */
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const items = computed(() => visibleAdminNav(router, auth.canAny))
const groups = computed(() => {
  const result: { title: AdminNavItem['group']; items: AdminNavItem[] }[] = []
  for (const item of items.value) {
    const last = result[result.length - 1]
    if (last && last.title === item.group) {
      last.items.push(item)
    } else {
      result.push({ title: item.group, items: [item] })
    }
  }
  return result
})

function isCurrent(item: AdminNavItem): boolean {
  if (route.name !== item.routeName) {
    return false
  }
  return Object.entries(item.query ?? {}).every(([key, value]) => route.query[key] === value)
}

async function logout(): Promise<void> {
  await auth.logout()
  await router.push({ name: 'home' })
}
</script>

<template>
  <div class="grid min-h-screen md:grid-cols-[224px_1fr]">
    <aside class="ls-side">
      <div class="ls-side__brand">
        LocalSpot
        <span class="mt-1 block text-caption normal-case text-ink-muted">Quản trị</span>
      </div>
      <nav aria-label="Điều hướng quản trị" class="flex flex-col gap-1">
        <template v-for="group in groups" :key="group.title ?? 'root'">
          <div v-if="group.title" class="ls-side__group">{{ group.title }}</div>
          <RouterLink
            v-for="item in group.items"
            :key="item.label"
            :to="{ name: item.routeName, query: item.query }"
            :aria-current="isCurrent(item) ? 'page' : undefined"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="item.icon" /></svg>
            {{ item.label }}
          </RouterLink>
        </template>
      </nav>
      <div v-if="auth.user" class="ls-side__role">
        <b>{{ auth.user.displayName }}</b>
        {{ ROLE_LABELS[primaryRole(auth.user.roles)] }}
        <div class="mt-3 flex items-center justify-between">
          <span class="flex gap-3">
            <RouterLink class="ls-link" :to="{ name: 'home' }">Trang chính</RouterLink>
            <button
              type="button"
              class="ls-link cursor-pointer border-0 bg-transparent p-0"
              @click="logout"
            >
              Đăng xuất
            </button>
          </span>
          <ThemeToggle />
        </div>
      </div>
    </aside>
    <main class="min-w-0 px-4 py-8 md:px-12">
      <RouterView />
    </main>
  </div>
</template>
