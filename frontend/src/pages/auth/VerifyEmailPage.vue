<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { authApi } from '@/api/auth'
import { problemMessage, toProblem } from '@/api/problem'
import LsButton from '@/components/ui/LsButton.vue'
import { useAuthStore } from '@/stores/auth'
import { retryAfterText } from '@/utils/format'

/**
 * `/verify-email` (UC02, P01).
 * - Có `?token=`: xác thực bằng link trong mail. Token sai / hết hạn / đã dùng cùng một câu (backend 410, không cho dò).
 * - Không có token: màn "Kiểm tra hộp thư" sau đăng ký, có nút gửi lại (chờ 45 giây giữa hai lần bấm như P01; backend
 *   còn giới hạn 3 lần / giờ).
 */
const RESEND_COOLDOWN_SECONDS = 45

const route = useRoute()
const auth = useAuthStore()

type VerifyState = 'verifying' | 'verified' | 'invalid' | 'error'
const token = computed(() => (typeof route.query.token === 'string' ? route.query.token : null))
const state = ref<VerifyState>('verifying')
const errorText = ref('')

const cooldown = ref(0)
const resendMessage = ref<{ ok: boolean; text: string } | null>(null)
let timer: ReturnType<typeof setInterval> | undefined

async function verify(value: string): Promise<void> {
  try {
    await authApi.verifyEmail(value)
    state.value = 'verified'
    await auth.reloadUser().catch(() => undefined)
  } catch (error) {
    const problem = toProblem(error)
    state.value = problem.code === 'TOKEN_INVALID' ? 'invalid' : 'error'
    errorText.value = problemMessage(problem)
  }
}

function startCooldown(seconds: number): void {
  cooldown.value = seconds
  clearInterval(timer)
  timer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) {
      clearInterval(timer)
    }
  }, 1000)
}

async function resend(): Promise<void> {
  resendMessage.value = null
  try {
    await authApi.resendVerification()
    resendMessage.value = { ok: true, text: 'Đã gửi lại liên kết xác thực.' }
    startCooldown(RESEND_COOLDOWN_SECONDS)
  } catch (error) {
    const problem = toProblem(error)
    const wait = problem.retryAfter ? ` Thử lại sau ${retryAfterText(problem.retryAfter)}.` : ''
    resendMessage.value = {
      ok: false,
      text: problem.retryAfter ? `Bạn đã gửi lại nhiều lần.${wait}` : problemMessage(problem),
    }
    if (problem.retryAfter) {
      startCooldown(problem.retryAfter)
    }
  }
}

onMounted(() => {
  if (token.value) {
    void verify(token.value)
  } else if (auth.user && !auth.user.emailVerified) {
    // Vừa đăng ký xong: mail đầu tiên vừa được gửi — chờ trước khi cho gửi lại
    startCooldown(RESEND_COOLDOWN_SECONDS)
  }
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <section class="flex flex-col gap-4" aria-live="polite">
    <!-- Mở từ link trong mail -->
    <template v-if="token">
      <template v-if="state === 'verifying'">
        <h1 class="ls-h2">Đang xác thực email…</h1>
      </template>
      <template v-else-if="state === 'verified'">
        <span class="state-icon state-icon--ok" aria-hidden="true">✓</span>
        <h1 class="ls-h2 m-0">Email đã được xác thực</h1>
        <p class="m-0 text-ink-muted">Giờ bạn có thể viết đánh giá và đóng góp cho cộng đồng.</p>
        <LsButton v-if="auth.isAuthenticated" :to="{ name: 'home' }" size="lg"
          >Về trang chủ</LsButton
        >
        <LsButton v-else :to="{ name: 'login' }" size="lg">Đăng nhập</LsButton>
      </template>
      <template v-else>
        <span class="state-icon" aria-hidden="true">✕</span>
        <h1 class="ls-h2 m-0">Không xác thực được</h1>
        <p class="m-0 text-ink-muted">
          {{
            state === 'invalid' ? 'Liên kết không hợp lệ, đã hết hạn hoặc đã được dùng.' : errorText
          }}
        </p>
        <p v-if="auth.isAuthenticated && !auth.user?.emailVerified" class="m-0">
          <RouterLink class="ls-link" :to="{ name: 'verify-email' }"
            >Gửi lại liên kết mới</RouterLink
          >
        </p>
        <p v-else-if="!auth.isAuthenticated" class="m-0 text-ink-muted">
          <RouterLink class="ls-link" :to="{ name: 'login', query: { redirect: '/verify-email' } }"
            >Đăng nhập</RouterLink
          >
          để nhận liên kết mới.
        </p>
      </template>
    </template>

    <!-- Sau đăng ký: kiểm tra hộp thư -->
    <template v-else-if="auth.user && !auth.user.emailVerified">
      <span class="state-icon state-icon--ok" aria-hidden="true">✓</span>
      <h1 class="ls-h2 m-0">Kiểm tra hộp thư của bạn</h1>
      <p class="m-0 text-caption text-ink-muted">
        Chúng tôi đã gửi liên kết xác thực tới <b class="text-ink">{{ auth.user.email }}</b
        >. Liên kết có hiệu lực trong 24 giờ.
      </p>
      <LsButton variant="ghost" size="lg" :disabled="cooldown > 0" @click="resend">
        {{ cooldown > 0 ? `Gửi lại email (sau ${cooldown} giây)` : 'Gửi lại email' }}
      </LsButton>
      <p
        v-if="resendMessage"
        :role="resendMessage.ok ? 'status' : 'alert'"
        :class="['m-0 text-caption', resendMessage.ok ? 'text-success' : 'text-danger']"
      >
        {{ resendMessage.ok ? '✓' : '✕' }} {{ resendMessage.text }}
      </p>
      <RouterLink class="ls-link" :to="{ name: 'home' }">Để sau, về trang chủ</RouterLink>
    </template>

    <template v-else-if="auth.user">
      <span class="state-icon state-icon--ok" aria-hidden="true">✓</span>
      <h1 class="ls-h2 m-0">Email của bạn đã được xác thực</h1>
      <LsButton :to="{ name: 'home' }" size="lg">Về trang chủ</LsButton>
    </template>

    <template v-else>
      <h1 class="ls-h2 m-0">Xác thực email</h1>
      <p class="m-0 text-ink-muted">
        Mở liên kết trong email chúng tôi đã gửi để xác thực. Cần gửi lại?
        <RouterLink class="ls-link" :to="{ name: 'login', query: { redirect: '/verify-email' } }"
          >Đăng nhập</RouterLink
        >
        rồi chọn "Gửi lại email".
      </p>
    </template>
  </section>
</template>

<style scoped>
/* Biểu tượng trạng thái tròn (ls-noteicon của P01): ký hiệu + chữ tiêu đề, không chỉ bằng màu */
.state-icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: var(--radius-pill);
  background: var(--surface-sunk);
  color: var(--ink);
  font: 700 24px/1 var(--font-sans);
}
.state-icon--ok {
  background: var(--olive);
  color: var(--on-olive);
}
</style>
