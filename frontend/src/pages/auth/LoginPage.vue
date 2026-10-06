<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { z } from 'zod'

import { toProblem } from '@/api/problem'
import FormError from '@/components/ui/FormError.vue'
import FormInput from '@/components/ui/FormInput.vue'
import LsButton from '@/components/ui/LsButton.vue'
import { splitServerErrors } from '@/composables/serverErrors'
import { safeRedirect } from '@/router/redirect'
import { useAuthStore } from '@/stores/auth'
import { emailSchema } from '@/validation/account'

/**
 * Đăng nhập (UC03, P01). Sai email hay sai mật khẩu cùng một câu — không cho dò email nào đã đăng ký. Bị khóa / quá số
 * lần thử dùng câu của backend (có thời hạn khóa / thời gian chờ).
 */
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const { handleSubmit, isSubmitting, setFieldError } = useForm({
  validationSchema: toTypedSchema(
    z.object({ email: emailSchema, password: z.string().min(1, 'Nhập mật khẩu.') }),
  ),
})
const formError = ref<string | null>(null)

/** Thông báo khi được chuyển tới từ luồng khác (đăng ký xong, đặt lại mật khẩu xong). */
const notice = computed(() => {
  if (route.query.reset === '1') {
    return '✓ Đã đặt mật khẩu mới. Hãy đăng nhập lại.'
  }
  if (route.query.registered === '1') {
    return '✓ Tài khoản đã được tạo. Hãy đăng nhập để tiếp tục.'
  }
  return null
})

const submit = handleSubmit(async ({ email, password }) => {
  formError.value = null
  try {
    await auth.login(email, password)
    await router.replace(safeRedirect(route.query.redirect))
  } catch (error) {
    const problem = toProblem(error)
    if (problem.code === 'INVALID_CREDENTIALS') {
      setFieldError('password', 'Email hoặc mật khẩu chưa đúng.')
      return
    }
    const { fieldErrors, formError: message } = splitServerErrors(problem, ['email', 'password'])
    Object.entries(fieldErrors).forEach(([field, msg]) =>
      setFieldError(field as 'email' | 'password', msg),
    )
    formError.value = message
  }
})
</script>

<template>
  <form class="flex flex-col" novalidate @submit="submit">
    <h1 class="ls-h1 auth-title">Đăng nhập</h1>
    <p class="mb-8 mt-2 text-ink-muted">Để viết đánh giá, check-in và lưu bộ sưu tập.</p>
    <p v-if="notice" role="status" class="mb-6 mt-0 text-success">{{ notice }}</p>
    <div class="flex flex-col gap-5">
      <FormInput name="email" label="Email" type="email" autocomplete="email" />
      <FormInput name="password" label="Mật khẩu" type="password" autocomplete="current-password" />
    </div>
    <div class="mb-7 mt-5 flex justify-end">
      <RouterLink class="ls-link text-caption" :to="{ name: 'forgot-password' }"
        >Quên mật khẩu?</RouterLink
      >
    </div>
    <FormError class="mb-4" :message="formError" />
    <LsButton type="submit" size="lg" block :loading="isSubmitting">Đăng nhập</LsButton>
  </form>
</template>

<style scoped>
.auth-title {
  font-size: 36px;
}
</style>
