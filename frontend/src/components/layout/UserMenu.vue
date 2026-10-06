<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useId } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { STAFF_PERMISSIONS } from '@/auth/permissions'
import LsAvatar from '@/components/ui/LsAvatar.vue'
import { useAuthStore } from '@/stores/auth'

/**
 * Menu ảnh đại diện ở header: trang quản trị (nhân sự), đổi mật khẩu, đăng xuất (UC04 — sitemap: đăng xuất là mục
 * trong menu, không có route). Mẫu "disclosure" (nút + danh sách liên kết): Escape / bấm ra ngoài để đóng.
 */
const auth = useAuthStore()
const router = useRouter()
const open = ref(false)
const root = ref<HTMLElement | null>(null)
const menuId = useId()

function close(): void {
  open.value = false
}

function onDocumentClick(event: MouseEvent): void {
  if (root.value && !root.value.contains(event.target as Node)) {
    close()
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    close()
  }
}

async function logout(): Promise<void> {
  close()
  await auth.logout()
  await router.push({ name: 'home' })
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div v-if="auth.user" ref="root" class="relative">
    <button
      type="button"
      class="rounded-pill"
      :aria-expanded="open"
      :aria-controls="menuId"
      :aria-label="`Tài khoản: ${auth.user.displayName}`"
      @click="open = !open"
    >
      <LsAvatar :name="auth.user.displayName" :src="auth.user.avatarUrl" size="sm" />
    </button>
    <div v-show="open" :id="menuId" class="ls-pop absolute right-0 top-12 z-20 w-64">
      <div class="border-b border-line px-4 py-3">
        <b class="block text-title">{{ auth.user.displayName }}</b>
        <span class="text-caption text-ink-muted">{{ auth.user.email }}</span>
      </div>
      <ul class="m-0 list-none p-2" @click="close">
        <li v-if="auth.canAny(STAFF_PERMISSIONS)">
          <RouterLink
            class="block rounded-xs px-3 py-2 hover:bg-surface-sunk"
            :to="{ name: 'admin' }"
          >
            Trang quản trị
          </RouterLink>
        </li>
        <li>
          <RouterLink
            class="block rounded-xs px-3 py-2 hover:bg-surface-sunk"
            :to="{ name: 'settings-password' }"
          >
            Đổi mật khẩu
          </RouterLink>
        </li>
        <li>
          <button
            type="button"
            class="block w-full cursor-pointer rounded-xs px-3 py-2 text-left hover:bg-surface-sunk"
            @click="logout"
          >
            Đăng xuất
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>
