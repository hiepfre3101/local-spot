<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { authApi } from '@/api/auth'
import { toProblem } from '@/api/problem'
import FormError from '@/components/ui/FormError.vue'
import FormInput from '@/components/ui/FormInput.vue'
import LsButton from '@/components/ui/LsButton.vue'
import LsSteps from '@/components/ui/LsSteps.vue'
import { splitServerErrors } from '@/composables/serverErrors'
import { useAuthStore } from '@/stores/auth'
import { withConfirmation } from '@/validation/account'

/**
 * Đặt mật khẩu mới — bước 3 (UC05, P01). Thành công: backend thu hồi mọi phiên (UC04), nên trình duyệt này cũng bỏ
 * phiên đang có và chuyển về đăng nhập.
 */
const STEPS = ['Nhập email', 'Mở liên kết', 'Đặt mật khẩu mới'] as const

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const token = computed(() => (typeof route.query.token === 'string' ? route.query.token : null))

const { handleSubmit, isSubmitting, setFieldError } = useForm({
  validationSchema: toTypedSchema(withConfirmation({})),
})
const formError = ref<string | null>(null)
const tokenInvalid = ref(false)

const submit = handleSubmit(async ({ newPassword }) => {
  if (!token.value) {
    return
  }
  formError.value = null
  try {
    await authApi.resetPassword(token.value, newPassword)
    auth.clearSession()
    await router.replace({ name: 'login', query: { reset: '1' } })
  } catch (error) {
    const problem = toProblem(error)
    if (problem.code === 'TOKEN_INVALID') {
      tokenInvalid.value = true
      return
    }
    const { fieldErrors, formError: message } = splitServerErrors(problem, ['newPassword'])
    if (fieldErrors.newPassword) {
      setFieldError('newPassword', fieldErrors.newPassword)
    }
    formError.value = message
  }
})
</script>

<template>
  <section class="flex flex-col gap-4">
    <LsSteps :steps="STEPS" :current="2" />
    <h1 class="ls-h2 m-0 mt-4">Đặt mật khẩu mới</h1>
    <template v-if="!token || tokenInvalid">
      <p role="alert" class="ls-field__error m-0">
        ✕ Liên kết không hợp lệ, đã hết hạn hoặc đã được dùng.
      </p>
      <RouterLink class="ls-link" :to="{ name: 'forgot-password' }"
        >Gửi lại liên kết mới</RouterLink
      >
    </template>
    <form v-else class="flex flex-col gap-5" novalidate @submit="submit">
      <FormInput
        name="newPassword"
        label="Mật khẩu mới"
        type="password"
        autocomplete="new-password"
        hint="Ít nhất 8 ký tự, có chữ và số."
      />
      <FormInput
        name="confirmPassword"
        label="Nhập lại mật khẩu"
        type="password"
        autocomplete="new-password"
      />
      <FormError :message="formError" />
      <LsButton type="submit" size="lg" block :loading="isSubmitting">Đặt lại mật khẩu</LsButton>
      <p class="m-0 text-caption text-ink-muted">
        Sau khi đặt lại, mọi phiên đăng nhập khác sẽ bị đăng xuất.
      </p>
    </form>
  </section>
</template>
