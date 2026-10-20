<script setup lang="ts">
import { useQueryClient } from '@tanstack/vue-query'
import { watch } from 'vue'
import { RouterView } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const queryClient = useQueryClient()

// Đổi người dùng (đăng xuất, đăng nhập tài khoản khác) → bỏ dữ liệu đã cache của người trước
watch(
  () => auth.user?.id,
  (current, previous) => {
    if (previous !== undefined && current !== previous) {
      queryClient.clear()
    }
  },
)
</script>

<template>
  <RouterView />
</template>
