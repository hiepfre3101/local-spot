<script setup lang="ts">
import { ref } from 'vue'

import { authApi } from '@/api/auth'
import { problemMessage, toProblem } from '@/api/problem'
import { useNotify } from '@/plugins/toast'
import { useAuthStore } from '@/stores/auth'

/**
 * Nhắc xác thực email (UC02) cho thành viên chưa xác thực — cần xác thực trước khi viết đánh giá (FR-22). Gửi lại link
 * ngay tại đây; backend giới hạn 3 lần / giờ (429 kèm thời gian chờ).
 */
const auth = useAuthStore()
const notify = useNotify()
const sending = ref(false)

async function resend(): Promise<void> {
  sending.value = true
  try {
    await authApi.resendVerification()
    notify.success(`Đã gửi lại liên kết xác thực tới ${auth.user?.email}.`)
  } catch (error) {
    notify.error(problemMessage(toProblem(error)))
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div
    v-if="auth.user && !auth.user.emailVerified"
    class="flex flex-wrap items-center justify-between gap-3 border-b border-line bg-surface-sunk px-4 py-2 text-caption md:px-12"
  >
    <span>● Bạn chưa xác thực email — cần xác thực trước khi viết đánh giá.</span>
    <button
      type="button"
      class="ls-link cursor-pointer border-0 bg-transparent p-0"
      :disabled="sending"
      @click="resend"
    >
      Gửi lại email xác thực
    </button>
  </div>
</template>
