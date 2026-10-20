<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { z } from 'zod'

import { authApi } from '@/api/auth'
import { toProblem } from '@/api/problem'
import FormError from '@/components/ui/FormError.vue'
import FormInput from '@/components/ui/FormInput.vue'
import LsButton from '@/components/ui/LsButton.vue'
import { splitServerErrors } from '@/composables/serverErrors'
import { useAuthStore } from '@/stores/auth'
import { displayNameSchema, emailSchema, newPasswordSchema } from '@/validation/account'

/**
 * Đăng ký (UC01, P01). Thành công thì **đăng nhập luôn** rồi sang "Kiểm tra hộp thư" (UC01 include UC02): gửi lại mail
 * xác thực cần đăng nhập (`/auth/resend-verification`), và người dùng không phải gõ lại mật khẩu vừa tạo. Chưa xác thực
 * vẫn đăng nhập được — chỉ chưa viết đánh giá được (FR-22).
 */
const auth = useAuthStore()
const router = useRouter()
const FIELDS = ['displayName', 'email', 'password'] as const

const { handleSubmit, isSubmitting, setFieldError } = useForm({
  validationSchema: toTypedSchema(
    z.object({ displayName: displayNameSchema, email: emailSchema, password: newPasswordSchema }),
  ),
})
const formError = ref<string | null>(null)

const submit = handleSubmit(async (values) => {
  formError.value = null
  try {
    await authApi.register(values)
  } catch (error) {
    const problem = toProblem(error)
    if (problem.code === 'EMAIL_ALREADY_EXISTS') {
      setFieldError('email', 'Email đã được sử dụng.')
      return
    }
    const { fieldErrors, formError: message } = splitServerErrors(problem, FIELDS)
    Object.entries(fieldErrors).forEach(([field, msg]) =>
      setFieldError(field as (typeof FIELDS)[number], msg),
    )
    formError.value = message
    return
  }
  try {
    await auth.login(values.email, values.password)
    await router.replace({ name: 'verify-email' })
  } catch {
    // Tài khoản đã tạo; đăng nhập tự động lỗi (mạng...) thì để người dùng tự đăng nhập
    await router.replace({ name: 'login', query: { registered: '1' } })
  }
})
</script>

<template>
  <form class="flex flex-col" novalidate @submit="submit">
    <h1 class="ls-h1 auth-title">Tạo tài khoản</h1>
    <p class="mb-8 mt-2 text-ink-muted">
      Kể về những góc phố bạn đã đến — thật, cụ thể, có ích cho người sau.
    </p>
    <div class="flex flex-col gap-5">
      <FormInput name="displayName" label="Tên hiển thị" autocomplete="nickname" />
      <FormInput name="email" label="Email" type="email" autocomplete="email" />
      <FormInput
        name="password"
        label="Mật khẩu"
        type="password"
        autocomplete="new-password"
        hint="Ít nhất 8 ký tự, có chữ và số."
      />
    </div>
    <FormError class="mt-5" :message="formError" />
    <LsButton class="mt-7" type="submit" size="lg" block :loading="isSubmitting"
      >Tạo tài khoản</LsButton
    >
  </form>
</template>

<style scoped>
.auth-title {
  font-size: 36px;
}
</style>
