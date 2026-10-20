<script setup lang="ts">
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import dayjs from 'dayjs'
import { computed, ref, useId, watch } from 'vue'

import { adminUsersApi } from '@/api/adminUsers'
import { problemMessage, toProblem } from '@/api/problem'
import { Permission, ROLE_DESCRIPTIONS, ROLE_LABELS } from '@/auth/permissions'
import LsAdminButton from '@/components/ui/LsAdminButton.vue'
import LsAvatar from '@/components/ui/LsAvatar.vue'
import LsBadge from '@/components/ui/LsBadge.vue'
import { useNotify } from '@/plugins/toast'
import { useAuthStore } from '@/stores/auth'
import type { AdminUser } from '@/types/api'
import { formatDate, formatDateTime, formatNumber } from '@/utils/format'

import {
  buildRoleList,
  editableRolesOf,
  hasRoleChanges,
  roleOptions,
  type EditableRole,
} from './roleRules'
import { STATUS_BADGE, userStatus } from './userStatus'

/**
 * Bảng chi tiết bên phải P10 (300px, tách bằng đường kẻ dọc — không hộp thoại, không thẻ): thông tin, vai trò, khóa /
 * mở khóa. Khóa bắt buộc thời hạn + lý do (O6, openapi); form khóa mở ngay trong bảng. Nút theo quyền:
 * `user:assign-role` cho vai trò, `user:lock` cho khóa. Admin không tự khóa mình (backend cũng chặn — 409).
 */
const props = defineProps<{ user: AdminUser }>()

const auth = useAuthStore()
const notify = useNotify()
const queryClient = useQueryClient()
const uid = useId()

const LOCK_DURATIONS = [
  { days: 1, label: '1 ngày' },
  { days: 7, label: '7 ngày' },
  { days: 30, label: '30 ngày' },
  { days: 365, label: '1 năm' },
] as const
const REASON_MAX = 500

const isSelf = computed(() => auth.user?.id === props.user.id)
const canAssign = computed(() => auth.can(Permission.USER_ASSIGN_ROLE))
const canLock = computed(() => auth.can(Permission.USER_LOCK) && !isSelf.value)
const status = computed(() => userStatus(props.user))

const draft = ref(editableRolesOf(props.user.roles))
const lockFormOpen = ref(false)
const lockDays = ref<number>(7)
const lockReason = ref('')
const lockReasonError = ref<string | null>(null)

// Chọn người khác / dữ liệu tải lại → bỏ phần đang sửa dở
watch(
  () => props.user,
  (user) => {
    draft.value = editableRolesOf(user.roles)
    lockFormOpen.value = false
    lockReason.value = ''
    lockReasonError.value = null
  },
)

const options = computed(() =>
  roleOptions({
    current: props.user.roles,
    draft: draft.value,
    isSelf: isSelf.value,
    canAssign: canAssign.value,
  }),
)
const dirty = computed(() => hasRoleChanges(props.user.roles, draft.value))

function toggle(role: EditableRole, checked: boolean): void {
  const next = new Set(draft.value)
  if (checked) {
    next.add(role)
  } else {
    next.delete(role)
  }
  draft.value = next
}

function refreshList(): Promise<void> {
  return queryClient.invalidateQueries({ queryKey: ['admin-users'] })
}

function onError(error: unknown): void {
  notify.error(problemMessage(toProblem(error)))
}

const saveRoles = useMutation({
  mutationFn: () =>
    adminUsersApi.replaceRoles(props.user.id, buildRoleList(props.user.roles, draft.value)),
  onSuccess: async () => {
    notify.success(`Đã cập nhật vai trò của ${props.user.displayName}.`)
    await refreshList()
    if (isSelf.value) {
      await auth.reloadUser()
    }
  },
  onError,
})

const lock = useMutation({
  mutationFn: () =>
    adminUsersApi.lock(
      props.user.id,
      dayjs().add(lockDays.value, 'day').toISOString(),
      lockReason.value.trim(),
    ),
  onSuccess: async () => {
    notify.success(`Đã khoá ${props.user.displayName}. Mọi phiên đăng nhập của họ đã bị thu hồi.`)
    lockFormOpen.value = false
    await refreshList()
  },
  onError,
})

const unlock = useMutation({
  mutationFn: () => adminUsersApi.unlock(props.user.id),
  onSuccess: async () => {
    notify.success(`Đã mở khoá ${props.user.displayName}.`)
    await refreshList()
  },
  onError,
})

function submitLock(): void {
  const reason = lockReason.value.trim()
  lockReasonError.value = !reason
    ? 'Nhập lý do khoá — người bị khoá sẽ thấy lý do này.'
    : reason.length > REASON_MAX
      ? `Lý do tối đa ${REASON_MAX} ký tự.`
      : null
  if (!lockReasonError.value) {
    lock.mutate()
  }
}
</script>

<template>
  <div class="panel flex flex-col gap-5">
    <div class="flex items-center gap-3">
      <LsAvatar :name="user.displayName" :src="user.avatarUrl" />
      <div class="min-w-0">
        <b class="block truncate">{{ user.displayName }}</b>
        <span class="block truncate text-caption text-ink-muted">{{ user.email }}</span>
      </div>
    </div>

    <dl class="detail-list">
      <dt>Trạng thái</dt>
      <dd>
        <LsBadge :tone="STATUS_BADGE[status].tone">{{ STATUS_BADGE[status].text }}</LsBadge>
      </dd>
      <dt>Tham gia</dt>
      <dd>{{ formatDate(user.createdAt) }}</dd>
      <dt>Điểm tin cậy</dt>
      <dd class="num">{{ formatNumber(user.trustScore) }}</dd>
      <template v-if="status === 'locked' && user.lockedUntil">
        <dt>Khoá đến</dt>
        <dd class="num">{{ formatDateTime(user.lockedUntil) }}</dd>
      </template>
    </dl>

    <fieldset class="ls-field m-0 border-0 p-0">
      <legend class="ls-field__label mb-2">Vai trò</legend>
      <div class="flex flex-col gap-2.5">
        <label v-for="option in options" :key="option.role" class="ls-check items-start">
          <input
            type="checkbox"
            class="mt-[3px]"
            :checked="option.checked"
            :disabled="option.disabled"
            @change="
              toggle(option.role as EditableRole, ($event.target as HTMLInputElement).checked)
            "
          />
          <span>
            {{ ROLE_LABELS[option.role] }}
            <small class="role-note">
              {{ option.note ?? ROLE_DESCRIPTIONS[option.role] }}
            </small>
          </span>
        </label>
      </div>
    </fieldset>

    <form
      v-if="lockFormOpen"
      class="flex flex-col gap-4 border-t border-line pt-4"
      novalidate
      @submit.prevent="submitLock"
    >
      <div class="ls-field">
        <label :for="`${uid}-days`">Thời hạn khoá</label>
        <span class="ls-selectwrap">
          <select :id="`${uid}-days`" v-model.number="lockDays" class="ls-select">
            <option v-for="d in LOCK_DURATIONS" :key="d.days" :value="d.days">{{ d.label }}</option>
          </select>
        </span>
      </div>
      <div class="ls-field">
        <label :for="`${uid}-reason`">Lý do</label>
        <textarea
          :id="`${uid}-reason`"
          v-model="lockReason"
          class="ls-textarea"
          :maxlength="REASON_MAX"
          :aria-invalid="lockReasonError ? 'true' : undefined"
          :aria-describedby="lockReasonError ? `${uid}-reason-error` : `${uid}-reason-hint`"
        />
        <span v-if="lockReasonError" :id="`${uid}-reason-error`" class="ls-field__error">
          ✕ {{ lockReasonError }}
        </span>
        <span v-else :id="`${uid}-reason-hint`" class="ls-field__hint">
          Người bị khoá thấy lý do này khi đăng nhập.
        </span>
      </div>
      <div class="flex flex-wrap gap-2">
        <LsAdminButton type="submit" variant="danger" :loading="lock.isPending.value"
          >Xác nhận khoá</LsAdminButton
        >
        <LsAdminButton @click="lockFormOpen = false">Huỷ</LsAdminButton>
      </div>
    </form>

    <div v-else class="flex flex-wrap gap-2 border-t border-line pt-4">
      <LsAdminButton
        v-if="canAssign"
        variant="primary"
        :disabled="!dirty"
        :loading="saveRoles.isPending.value"
        @click="saveRoles.mutate()"
      >
        Lưu vai trò
      </LsAdminButton>
      <template v-if="canLock">
        <LsAdminButton
          v-if="status === 'locked'"
          :loading="unlock.isPending.value"
          @click="unlock.mutate()"
        >
          Mở khoá
        </LsAdminButton>
        <LsAdminButton variant="danger" @click="lockFormOpen = true">
          {{ status === 'locked' ? 'Đổi thời hạn khoá' : 'Khoá tài khoản' }}
        </LsAdminButton>
      </template>
    </div>
  </div>
</template>

<style scoped>
/* Cỡ chữ theo P10: bảng chi tiết 14/20, mô tả vai trò 12/16 */
.panel {
  font: 400 14px/20px var(--font-sans);
}
.panel .ls-check {
  font-size: 14px;
}
.role-note {
  display: block;
  font: 400 12px/16px var(--font-sans);
  color: var(--ink-muted);
}
.detail-list {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-4) 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}
.detail-list dt {
  color: var(--ink-muted);
}
.detail-list dd {
  margin: 0;
  text-align: right;
}
</style>
