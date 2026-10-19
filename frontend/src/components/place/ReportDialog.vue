<script setup lang="ts">
import { computed, ref, useId, useTemplateRef } from 'vue'

import { interactionsApi } from '@/api/interactions'
import { problemMessage, toProblem } from '@/api/problem'
import LsButton from '@/components/ui/LsButton.vue'
import { useNotify } from '@/plugins/toast'
import type { ReportReason } from '@/types/api'

import { REPORT_REASONS, type ReportTarget } from './reportTarget'

/**
 * Báo cáo vi phạm (UC17, FR-25) — một hộp thoại dùng chung cho địa điểm, đánh giá, bình luận trên trang chi tiết. Lý do
 * "Thông tin sai" / "Khác" bắt buộc mô tả (chốt 2026-10-14). Mỗi người báo cáo một đối tượng một lần (U10): đã báo cáo
 * thì nói rõ, không coi là lỗi. Gọi `open({ type, id, label })`.
 */
const DETAIL_MAX = 1000

const id = useId()
const notify = useNotify()
const dialog = useTemplateRef<HTMLDialogElement>('dialog')
const target = ref<ReportTarget | null>(null)
const reason = ref<ReportReason | null>(null)
const detail = ref('')
const error = ref<string | null>(null)
const sending = ref(false)

const needsDetail = computed(() => reason.value === 'WRONG_INFO' || reason.value === 'OTHER')

function open(next: ReportTarget): void {
  target.value = next
  reason.value = null
  detail.value = ''
  error.value = null
  dialog.value?.showModal()
}

defineExpose({ open })

function close(): void {
  dialog.value?.close()
}

async function submit(): Promise<void> {
  const t = target.value
  if (!t) return
  if (!reason.value) {
    error.value = 'Chọn một lý do.'
    return
  }
  const text = detail.value.trim()
  if (needsDetail.value && !text) {
    error.value = 'Mô tả cụ thể điều chưa đúng để kiểm duyệt viên kiểm tra.'
    return
  }
  error.value = null
  sending.value = true
  try {
    await interactionsApi.report(t.type, t.id, reason.value, text)
    notify.success('Đã gửi báo cáo. Kiểm duyệt viên sẽ xem xét.')
    close()
  } catch (cause) {
    const problem = toProblem(cause)
    if (problem.code === 'REPORT_ALREADY_EXISTS') {
      notify.info('Bạn đã báo cáo nội dung này rồi — mỗi người chỉ báo cáo một lần.')
      close()
    } else {
      error.value = problemMessage(problem)
    }
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <dialog
    ref="dialog"
    class="ls-pop m-auto w-[min(520px,calc(100vw-32px))] p-0 text-ink backdrop:bg-ink/60"
    :aria-labelledby="`${id}-title`"
  >
    <form class="ls-pub flex flex-col gap-5 p-6" novalidate @submit.prevent="submit">
      <h2 :id="`${id}-title`" class="m-0 text-title">Báo cáo {{ target?.label }}</h2>
      <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
        <legend class="mb-2 font-semibold">Lý do</legend>
        <label v-for="option in REPORT_REASONS" :key="option.value" class="ls-check">
          <input v-model="reason" type="radio" :name="`${id}-reason`" :value="option.value" />
          {{ option.label }}
        </label>
      </fieldset>
      <div class="ls-field">
        <label :for="`${id}-detail`">Mô tả{{ needsDetail ? ' *' : ' (tuỳ chọn)' }}</label>
        <textarea
          :id="`${id}-detail`"
          v-model="detail"
          class="ls-textarea"
          rows="3"
          :maxlength="DETAIL_MAX"
        ></textarea>
      </div>
      <p v-if="error" role="alert" class="ls-field__error m-0">✕ {{ error }}</p>
      <div class="flex flex-wrap justify-end gap-3">
        <LsButton variant="ghost" @click="close">Huỷ</LsButton>
        <LsButton type="submit" :loading="sending">Gửi báo cáo</LsButton>
      </div>
    </form>
  </dialog>
</template>
