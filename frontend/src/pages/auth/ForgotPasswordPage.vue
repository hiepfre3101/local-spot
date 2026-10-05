<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { z } from 'zod'

import { authApi } from '@/api/auth'
import { problemMessage, toProblem } from '@/api/problem'
import FormError from '@/components/ui/FormError.vue'
import FormInput from '@/components/ui/FormInput.vue'
import LsButton from '@/components/ui/LsButton.vue'
import LsSteps from '@/components/ui/LsSteps.vue'
import { emailSchema } from '@/validation/account'

/**
 * Khôi phục mật khẩu — bước 1 (UC05, P01). Backend luôn trả 204 kể cả email không tồn tại, nên câu xác nhận viết
 * dạng "nếu…" — không tiết lộ email nào đã đăng ký.
 */
const STEPS = ['Nhập email', 'Mở liên kết', 'Đặt mật khẩu mới'] as const

const { handleSubmit, isSubmitting } = useForm({
  validationSchema: toTypedSchema(z.object({ email: emailSchema })),
})
const sentTo = ref<string | null>(null)
const formError = ref<string | null>(null)

const submit = handleSubmit(async ({ email }) => {
  formError.value = null
  try {
    await authApi.forgotPassword(email)
    sentTo.value = email
  } catch (error) {
    formError.value = problemMessage(toProblem(error))
  }
})
</script>

<template>
  <section class="flex flex-col gap-4">
    <LsSteps :steps="STEPS" :current="sentTo ? 1 : 0" />
    <h1 class="ls-h2 m-0 mt-4">Khôi phục mật khẩu</h1>
    <template v-if="sentTo">
      <p role="status" class="m-0 text-ink-muted">
        Nếu <b class="text-ink">{{ sentTo }}</b> đã đăng ký, bạn sẽ nhận được liên kết đặt lại mật
        khẩu trong vài phút. Liên kết có hiệu lực 30 phút và chỉ dùng được một lần.
      </p>
      <RouterLink class="ls-link" :to="{ name: 'login' }">Quay lại đăng nhập</RouterLink>
    </template>
    <form v-else class="flex flex-col gap-5" novalidate @submit="submit">
      <p class="m-0 text-ink-muted">
        Nhập email bạn đã đăng ký, chúng tôi sẽ gửi liên kết đặt mật khẩu mới.
      </p>
      <FormInput name="email" label="Email" type="email" autocomplete="email" />
      <FormError :message="formError" />
      <LsButton type="submit" size="lg" block :loading="isSubmitting">Gửi liên kết</LsButton>
      <RouterLink class="ls-link text-caption" :to="{ name: 'login' }"
        >Quay lại đăng nhập</RouterLink
      >
    </form>
  </section>
</template>
