<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import ThemeToggle from '@/components/layout/ThemeToggle.vue'
import UserMenu from '@/components/layout/UserMenu.vue'
import VerifyEmailBanner from '@/components/layout/VerifyEmailBanner.vue'
import { visiblePublicNav } from '@/components/layout/publicNav'
import LsButton from '@/components/ui/LsButton.vue'
import { useAuthStore } from '@/stores/auth'

/**
 * Khung trang công khai (sitemap §1): header logo · điều hướng · Đăng nhập / Đăng ký hoặc menu tài khoản; chân trang
 * `olive` với chữ LOCALSPOT cỡ wordmark (HomePage README). Chuông thông báo thêm cùng module Thông báo.
 */
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const nav = computed(() => visiblePublicNav(router))
/** Đăng nhập xong quay lại đúng trang đang xem (sitemap §2). */
const loginTarget = computed(() => ({ name: 'login', query: { redirect: route.fullPath } }))
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <a
      href="#main"
      class="sr-only rounded-pill bg-surface-raised px-4 py-2 focus:not-sr-only focus:absolute focus:left-4 focus:top-4"
    >
      Bỏ qua điều hướng
    </a>
    <div class="mx-auto w-full max-w-[1200px] px-4 md:px-12">
      <header class="ls-nav">
        <RouterLink class="ls-nav__logo" :to="{ name: 'home' }">LocalSpot</RouterLink>
        <nav v-if="nav.length" aria-label="Điều hướng chính" class="max-md:hidden">
          <RouterLink
            v-for="item in nav"
            :key="item.label"
            :to="{ name: item.routeName, query: item.query }"
          >
            {{ item.label }}
          </RouterLink>
        </nav>
        <div class="flex items-center gap-2">
          <ThemeToggle />
          <UserMenu v-if="auth.isAuthenticated" />
          <template v-else>
            <LsButton variant="ghost" size="sm" :to="loginTarget">Đăng nhập</LsButton>
            <LsButton size="sm" :to="{ name: 'register' }">Đăng ký</LsButton>
          </template>
        </div>
      </header>
    </div>
    <VerifyEmailBanner />

    <main id="main" class="mx-auto w-full max-w-[1200px] flex-1 px-4 pb-16 pt-8 md:px-12">
      <RouterView />
    </main>

    <footer class="bg-olive text-on-olive">
      <div class="mx-auto max-w-[1200px] px-4 pb-6 pt-12 md:px-12">
        <div class="border-b border-on-olive pb-10">
          <b class="text-label uppercase">LocalSpot</b>
          <p class="mt-2 max-w-[48ch]">Nền tảng đánh giá địa điểm du lịch do cộng đồng viết.</p>
        </div>
        <svg class="mt-6 block h-auto w-full" viewBox="0 0 1104 190" aria-hidden="true">
          <text
            x="0"
            y="176"
            textLength="1104"
            lengthAdjust="spacingAndGlyphs"
            class="fill-on-olive"
            font-size="210"
            font-weight="800"
          >
            LOCALSPOT
          </text>
        </svg>
      </div>
    </footer>
  </div>
</template>
