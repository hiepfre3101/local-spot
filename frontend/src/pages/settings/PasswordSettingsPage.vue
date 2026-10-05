<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { ref } from 'vue'
import { z } from 'zod'

import { meApi } from '@/api/me'
import { toProblem } from '@/api/problem'
import FormError from '@/components/ui/FormError.vue'
import FormInput from '@/components/ui/FormInput.vue'
import LsButton from '@/components/ui/LsButton.vue'
import { splitServerErrors } from '@/composables/serverErrors'
import { useNotify } from '@/plugins/toast'
import { withConfirmation } from '@/validation/account'

import SettingsShell from './SettingsShell.vue'

/**
 * Cài đặt — Mật khẩu (UC06, P06). Mật khẩu hiện tại sai → 422 gắn vào ô đó (không phải 401 — 401 là hết phiên, chốt
 * 2026-10-03). Thành công: các thiết bị khác bị đăng xuất, thiết bị này nhận cookie mới nên vẫn giữ phiên.
 */
const FIELDS = ['currentPassword', 'newPassword', 'confirmPassword'] as const
const notify = useNotify()

const { handleSubmit, isSubmitting, setFieldError, resetForm } = useForm({
  validationSchema: toTypedSchema(
    withConfirmation({ currentPassword: z.string().min(1, 'Nhập mật khẩu hiện tại.') }),
  ),
})
const formError = ref<string | null>(null)

const submit = handleSubmit(async ({ currentPassword, newPassword }) => {
  formError.value = null
  try {
    await meApi.changePassword(currentPassword, newPassword)
    resetForm()
    notify.success('Đã đổi mật khẩu. Các thiết bị khác đã được đăng xuất.')
  } catch (error) {
    const problem = toProblem(error)
    const { fieldErrors, formError: message } = splitServerErrors(problem, FIELDS)
    Object.entries(fieldErrors).forEach(([field, msg]) =>
      setFieldError(field as (typeof FIELDS)[number], msg),
    )
    formError.value = message
  }
})
</script>

<template>
  <SettingsShell title="Mật khẩu">
    <form class="ls-pub flex max-w-[420px] flex-col gap-5" novalidate @submit="submit">
      <FormInput
        name="currentPassword"
        label="Mật khẩu hiện tại"
        type="password"
        autocomplete="current-password"
      />
      <FormInput
        name="newPassword"
        label="Mật khẩu mới"
        type="password"
        autocomplete="new-password"
        hint="Ít nhất 8 ký tự, có chữ và số."
      />
      <FormInput
        name="confirmPassword"
        label="Nhập lại mật khẩu mới"
        type="password"
        autocomplete="new-password"
      />
      <FormError :message="formError" />
      <div>
        <LsButton type="submit" :loading="isSubmitting">Đổi mật khẩu</LsButton>
      </div>
    </form>
  </SettingsShell>
</template>
