<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, useId } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { moderationApi } from '@/api/moderation'
import { problemMessage, toProblem } from '@/api/problem'
import { REPORT_REASONS } from '@/components/place/reportTarget'
import LsAdminButton from '@/components/ui/LsAdminButton.vue'
import LsBadge from '@/components/ui/LsBadge.vue'
import { useAuthStore } from '@/stores/auth'
import type { ReportReason, ReportTargetType } from '@/types/api'
import { formatDateTime, formatNumber } from '@/utils/format'

import ModerationHead from './ModerationHead.vue'
import { REPORT_TARGET_LABELS } from './moderation'
import { useDecision } from './useDecision'

/**
 * Xử lý báo cáo vi phạm (UC29) — mọi báo cáo đang mở về **một đối tượng** xử lý cùng lúc: Ẩn (ghi vi phạm theo luật O9)
 * hoặc Bỏ qua. Không có thao tác sửa nội dung (O8). Lý do xác nhận phải là một lý do có trong các báo cáo; bắt buộc khi
 * ẩn địa điểm vì nó quyết định người đề xuất có bị ghi vi phạm không. Màn nói trước hậu quả của từng nút.
 */
const route = useRoute()
const auth = useAuthStore()
const uid = useId()
const targetType = computed(() => route.params.targetType as ReportTargetType)
const targetId = computed(() => Number(route.params.targetId))

const detail = useQuery({
  queryKey: computed(() => ['moderation', 'report', targetType.value, targetId.value]),
  queryFn: () => moderationApi.reportGroup(targetType.value, targetId.value),
  staleTime: 0,
  retry: false,
})
const data = computed(() => detail.data.value ?? null)
const group = computed(() => data.value?.group ?? null)
const loadProblem = computed(() => (detail.error.value ? toProblem(detail.error.value) : null))

const deleted = computed(() => group.value?.targetStatus === 'DELETED')
const reasonOptions = computed(() =>
  REPORT_REASONS.filter((r) => (group.value?.reasons[r.value] ?? 0) > 0),
)
const selfAction = computed(() => {
  if (!auth.user || !data.value) return false
  if (data.value.author?.id === auth.user.id) return true
  return targetType.value === 'PLACE' && auth.user.ownedPlaceIds.includes(targetId.value)
})

/** Hậu quả của "Ẩn" theo loại đối tượng (O9, chốt 2026-10-14). */
const hideConsequence = computed(() => {
  const who = data.value?.author?.displayName ?? 'tác giả'
  if (deleted.value) return 'Đối tượng đã bị xoá — chỉ đóng các báo cáo, không ghi vi phạm.'
  switch (targetType.value) {
    case 'REVIEW':
    case 'COMMENT':
      return `Ẩn nội dung và ghi một vi phạm cho ${who}.`
    case 'USER':
      return `Không có gì để ẩn — chỉ ghi một vi phạm cho ${who}. Khoá tài khoản ở mục Người dùng & vai trò.`
    case 'PLACE':
      return `Ẩn địa điểm khỏi trang công khai. Người đề xuất (${who}) chỉ bị ghi vi phạm khi lý do là Spam, Giả mạo hoặc Xúc phạm — chủ quán không bao giờ bị ghi.`
  }
  return ''
})

const confirmedReason = ref<ReportReason | ''>('')
const note = ref('')
const reasonError = ref<string | null>(null)
const { submitting, error, submit } = useDecision('REPORT', () => detail.refetch())

async function decide(decision: 'HIDE' | 'DISMISS'): Promise<void> {
  if (
    decision === 'HIDE' &&
    targetType.value === 'PLACE' &&
    !deleted.value &&
    !confirmedReason.value
  ) {
    reasonError.value = 'Chọn lý do xác nhận trước khi ẩn địa điểm.'
    document.getElementById(`${uid}-reason-0`)?.focus()
    return
  }
  reasonError.value = null
  await submit(
    () =>
      moderationApi.decideReports({
        targetType: targetType.value,
        targetId: targetId.value,
        decision,
        confirmedReason: confirmedReason.value || undefined,
        note: note.value.trim() || undefined,
      }),
    decision === 'HIDE' ? 'Đã ẩn nội dung và đóng các báo cáo.' : 'Đã bỏ qua các báo cáo.',
  )
}
</script>

<template>
  <div>
    <p v-if="detail.isPending.value" class="text-ink-muted">Đang tải…</p>
    <div v-else-if="loadProblem">
      <template v-if="loadProblem.code === 'REPORT_NOT_FOUND'">
        <h1 class="m-0 text-heading">Không còn báo cáo đang mở</h1>
        <p class="text-ink-muted">
          Các báo cáo về {{ REPORT_TARGET_LABELS[targetType].toLowerCase() }} này đã được xử lý.
        </p>
        <RouterLink class="ls-link" :to="{ name: 'admin-queue', query: { type: 'REPORT' } }"
          >Về hàng chờ báo cáo</RouterLink
        >
      </template>
      <p v-else role="alert" class="ls-field__error">✕ {{ problemMessage(loadProblem) }}</p>
    </div>

    <template v-else-if="data && group">
      <ModerationHead
        type="REPORT"
        :ref-label="`${REPORT_TARGET_LABELS[group.targetType]} #${group.targetId}`"
        :title="group.targetPreview ?? '(Nội dung đã bị xoá)'"
      >
        <template #meta>
          <LsBadge tone="danger">⚑ {{ formatNumber(group.reportCount) }} báo cáo</LsBadge>
          <span>{{ REPORT_TARGET_LABELS[group.targetType] }}</span>
          <span v-if="data.author"
            >Của <b>{{ data.author.displayName }}</b></span
          >
          <span>Báo cáo đầu tiên {{ formatDateTime(group.firstReportedAt) }}</span>
        </template>
        <template #actions>
          <LsAdminButton :disabled="selfAction" :loading="submitting" @click="decide('DISMISS')">
            Bỏ qua báo cáo
          </LsAdminButton>
          <LsAdminButton
            variant="danger"
            :disabled="selfAction"
            :loading="submitting"
            @click="decide('HIDE')"
          >
            {{ targetType === 'USER' || deleted ? 'Xác nhận vi phạm' : 'Ẩn nội dung' }}
          </LsAdminButton>
        </template>
      </ModerationHead>

      <p v-if="error" role="alert" class="ls-field__error mt-4">✕ {{ error }}</p>
      <p v-if="selfAction" class="mt-4 text-caption text-ink-muted">
        ● Nội dung của chính bạn hoặc địa điểm bạn sở hữu — một kiểm duyệt viên khác sẽ xử lý.
      </p>

      <section class="ls-section fs">
        <div class="fs__intro">
          <h2>Nội dung bị báo cáo</h2>
          <p>Không sửa được nội dung — chỉ ẩn hoặc bỏ qua.</p>
        </div>
        <dl class="ls-dl fs__grid">
          <div class="col-span-full">
            <dt>{{ REPORT_TARGET_LABELS[group.targetType] }}</dt>
            <dd>{{ group.targetPreview ?? '(Đã bị xoá)' }}</dd>
          </div>
          <div>
            <dt>Trạng thái hiện tại</dt>
            <dd>{{ group.targetStatus }}</dd>
          </div>
          <div>
            <dt>Người sẽ bị ghi vi phạm</dt>
            <dd>
              <RouterLink
                v-if="data.author"
                class="ls-link"
                :to="{ name: 'profile', params: { id: data.author.id } }"
                >{{ data.author.displayName }}</RouterLink
              >
              <template v-else>—</template>
            </dd>
          </div>
        </dl>
      </section>

      <section class="ls-section fs">
        <div class="fs__intro">
          <h2>Các báo cáo</h2>
          <p>Đang mở, cũ trước.</p>
        </div>
        <div class="max-w-[720px] overflow-x-auto">
          <table class="ls-table">
            <thead>
              <tr>
                <th scope="col">Người báo cáo</th>
                <th scope="col">Lý do</th>
                <th scope="col">Gửi lúc</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="report in data.reports" :key="report.id">
                <td class="whitespace-nowrap">{{ report.reporter.displayName }}</td>
                <td>
                  {{
                    REPORT_REASONS.find((r) => r.value === report.reason)?.label ?? report.reason
                  }}
                  <div v-if="report.detail" class="text-caption text-ink-muted">
                    {{ report.detail }}
                  </div>
                </td>
                <td class="num whitespace-nowrap">{{ formatDateTime(report.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="ls-section fs">
        <div class="fs__intro">
          <h2>Quyết định</h2>
          <p>{{ hideConsequence }} "Bỏ qua" đóng các báo cáo, không ẩn gì và không ghi vi phạm.</p>
        </div>
        <div class="fs__grid">
          <fieldset
            class="col-span-full m-0 border-0 p-0"
            :aria-invalid="reasonError ? true : undefined"
            :aria-describedby="reasonError ? `${uid}-reason-error` : undefined"
          >
            <legend class="ls-field__label mb-2">
              Lý do xác nhận{{
                targetType === 'PLACE' && !deleted ? ' * (khi ẩn)' : ' (không bắt buộc)'
              }}
            </legend>
            <div class="flex flex-wrap gap-x-6 gap-y-2">
              <label v-for="(option, i) in reasonOptions" :key="option.value" class="ls-check">
                <input
                  :id="`${uid}-reason-${i}`"
                  v-model="confirmedReason"
                  type="radio"
                  :name="`${uid}-reason`"
                  :value="option.value"
                />
                {{ option.label }} · {{ formatNumber(group.reasons[option.value] ?? 0) }}
              </label>
            </div>
            <span v-if="reasonError" :id="`${uid}-reason-error`" class="ls-field__error"
              >✕ {{ reasonError }}</span
            >
          </fieldset>
          <div class="ls-field col-span-full">
            <label :for="`${uid}-note`">Ghi chú nội bộ (không bắt buộc)</label>
            <textarea
              :id="`${uid}-note`"
              v-model="note"
              class="ls-textarea"
              maxlength="500"
            ></textarea>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>
