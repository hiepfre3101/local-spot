<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, useTemplateRef } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { moderationApi } from '@/api/moderation'
import { problemMessage, toProblem } from '@/api/problem'
import LsAdminButton from '@/components/ui/LsAdminButton.vue'
import LsBadge from '@/components/ui/LsBadge.vue'
import { useAuthStore } from '@/stores/auth'
import type { ModerationClaim } from '@/types/api'
import { formatDateTime, formatNumber } from '@/utils/format'

import EvidenceImage from './EvidenceImage.vue'
import ModerationHead from './ModerationHead.vue'
import RejectReason from './RejectReason.vue'
import { useDecision } from './useDecision'

/**
 * Duyệt yêu cầu xác nhận sở hữu (UC30): địa điểm, người yêu cầu, số điện thoại liên hệ, ghi chú và ảnh minh chứng.
 * Duyệt → người yêu cầu thành chủ ngay; các yêu cầu đang chờ khác của cùng địa điểm **tự bị từ chối** — màn nói rõ số
 * lượng trước khi bấm. Từ chối không ghi vi phạm, người dùng gửi lại được.
 */
const route = useRoute()
const auth = useAuthStore()
const claimId = computed(() => Number(route.params.id))

const detail = useQuery({
  queryKey: computed(() => ['moderation', 'claim', claimId.value]),
  queryFn: () => moderationApi.claim(claimId.value),
  staleTime: 0,
})
const claim = computed(() => detail.data.value ?? null)
const loadError = computed(() =>
  detail.error.value ? problemMessage(toProblem(detail.error.value)) : null,
)

const STATUS: Record<
  ModerationClaim['status'],
  { tone: 'info' | 'success' | 'danger'; text: string }
> = {
  PENDING: { tone: 'info', text: '● Chờ xác nhận' },
  APPROVED: { tone: 'success', text: '✓ Đã xác nhận sở hữu' },
  REJECTED: { tone: 'danger', text: '✕ Đã từ chối' },
}

const pending = computed(() => claim.value?.status === 'PENDING')
const ownClaim = computed(() => claim.value?.claimant.id === auth.user?.id)

const reason = ref('')
const reasonError = ref<string | null>(null)
const reasonSection = useTemplateRef<HTMLElement>('reasonSection')
const { submitting, error, submit } = useDecision('CLAIM', () => detail.refetch())

async function approve(): Promise<void> {
  if (!claim.value) return
  const version = claim.value.version
  await submit(
    () => moderationApi.decideClaim(claimId.value, { decision: 'APPROVE', version }),
    'Đã xác nhận chủ sở hữu.',
  )
}

async function reject(): Promise<void> {
  if (!claim.value) return
  if (!reason.value.trim()) {
    reasonError.value = 'Chọn lý do (hoặc ghi rõ lý do khác) trước khi từ chối.'
    reasonSection.value?.querySelector('select')?.focus()
    return
  }
  reasonError.value = null
  const version = claim.value.version
  await submit(
    () =>
      moderationApi.decideClaim(claimId.value, {
        decision: 'REJECT',
        reason: reason.value.trim(),
        version,
      }),
    'Đã từ chối yêu cầu.',
  )
}
</script>

<template>
  <div>
    <p v-if="detail.isPending.value" class="text-ink-muted">Đang tải…</p>
    <p v-else-if="loadError" role="alert" class="ls-field__error">✕ {{ loadError }}</p>

    <template v-else-if="claim">
      <ModerationHead type="CLAIM" :ref-label="`#${claim.id}`" :title="claim.place.name">
        <template #meta>
          <LsBadge :tone="STATUS[claim.status].tone">{{ STATUS[claim.status].text }}</LsBadge>
          <span
            >Yêu cầu bởi <b>{{ claim.claimant.displayName }}</b></span
          >
          <time :datetime="claim.createdAt">{{ formatDateTime(claim.createdAt) }}</time>
          <span>{{ formatNumber(claim.evidenceCount) }} minh chứng</span>
        </template>
        <template v-if="pending" #actions>
          <LsAdminButton
            variant="danger"
            :disabled="ownClaim"
            :loading="submitting"
            @click="reject"
          >
            Từ chối
          </LsAdminButton>
          <LsAdminButton
            variant="primary"
            :disabled="ownClaim"
            :loading="submitting"
            @click="approve"
          >
            Xác nhận chủ sở hữu
          </LsAdminButton>
        </template>
      </ModerationHead>

      <p v-if="error" role="alert" class="ls-field__error mt-4">✕ {{ error }}</p>
      <p v-if="pending && ownClaim" class="mt-4 text-caption text-ink-muted">
        ● Đây là yêu cầu của chính bạn — một kiểm duyệt viên khác sẽ duyệt.
      </p>
      <p v-if="pending && claim.otherPendingClaimCount > 0" class="mt-4 mb-0">
        ▲ Còn {{ formatNumber(claim.otherPendingClaimCount) }} yêu cầu khác đang chờ cho địa điểm
        này — xác nhận yêu cầu này thì các yêu cầu đó tự bị từ chối.
      </p>
      <p v-if="!pending" class="mt-4 mb-0">
        Yêu cầu này đã được xử lý<template v-if="claim.rejectReason">
          — lý do từ chối: <b>{{ claim.rejectReason }}</b></template
        >.
      </p>

      <section class="ls-section fs">
        <div class="fs__intro">
          <h2>Địa điểm</h2>
          <p>Người yêu cầu thành chủ ngay khi được xác nhận.</p>
        </div>
        <dl class="ls-dl fs__grid">
          <div class="col-span-full">
            <dt>Tên</dt>
            <dd>
              <RouterLink
                class="ls-link"
                :to="{ name: 'place', params: { slug: claim.place.slug } }"
                >{{ claim.place.name }}</RouterLink
              >
            </dd>
          </div>
          <div class="col-span-full">
            <dt>Địa chỉ</dt>
            <dd>{{ claim.place.address }}</dd>
          </div>
        </dl>
      </section>

      <section class="ls-section fs">
        <div class="fs__intro">
          <h2>Người yêu cầu</h2>
          <p>Gọi số liên hệ để xác minh nếu minh chứng chưa rõ.</p>
        </div>
        <dl class="ls-dl fs__grid">
          <div>
            <dt>Tài khoản</dt>
            <dd>
              <RouterLink
                class="ls-link"
                :to="{ name: 'profile', params: { id: claim.claimant.id } }"
                >{{ claim.claimant.displayName }}</RouterLink
              >
            </dd>
          </div>
          <div>
            <dt>Số điện thoại liên hệ</dt>
            <dd>
              <a class="ls-link" :href="`tel:${claim.contactPhone}`">{{ claim.contactPhone }}</a>
            </dd>
          </div>
          <div class="col-span-full">
            <dt>Ghi chú</dt>
            <dd>{{ claim.note || '—' }}</dd>
          </div>
        </dl>
      </section>

      <section class="ls-section fs">
        <div class="fs__intro">
          <h2>Minh chứng</h2>
          <p>Giấy phép kinh doanh, hoá đơn… Bấm vào ảnh để xem cỡ gốc.</p>
        </div>
        <div v-if="claim.evidenceCount" class="ls-thumbs">
          <EvidenceImage
            v-for="i in claim.evidenceCount"
            :key="i"
            :claim-id="claim.id"
            :index="i - 1"
          />
        </div>
        <p v-else class="m-0 text-ink-muted">Không có minh chứng.</p>
      </section>

      <div v-if="pending && !ownClaim" ref="reasonSection">
        <RejectReason
          v-model="reason"
          :presets="[
            'Minh chứng không rõ hoặc không hợp lệ',
            'Không liên hệ được số điện thoại',
            'Thông tin không khớp với địa điểm',
          ]"
          :error="reasonError"
          hint="Từ chối không ghi vi phạm; người yêu cầu nhận lý do và gửi lại được."
        />
      </div>
    </template>
  </div>
</template>
