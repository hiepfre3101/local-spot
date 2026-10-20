<script setup lang="ts">
import { useInfiniteQuery } from '@tanstack/vue-query'
import { computed, ref, useId, watch } from 'vue'

import { adminUsersApi, type AdminUserFilter } from '@/api/adminUsers'
import { problemMessage, toProblem } from '@/api/problem'
import { ROLE_LABELS, primaryRole } from '@/auth/permissions'
import LsAdminButton from '@/components/ui/LsAdminButton.vue'
import LsBadge from '@/components/ui/LsBadge.vue'
import type { AdminUser, RoleName } from '@/types/api'
import { formatNumber } from '@/utils/format'

import UserDetailPanel from './UserDetailPanel.vue'
import { STATUS_BADGE, userStatus } from './userStatus'

/**
 * Người dùng & vai trò (UC31, P10): bộ lọc một hàng (tìm, vai trò, trạng thái) · bảng · bảng chi tiết 300px tách bằng
 * đường kẻ dọc. Phân trang cursor nên dùng "Tải thêm" thay cho số trang (API không trả tổng số — không COUNT bảng
 * users mỗi lần lọc). Lọc "Chưa xác thực" chưa có ở API nên chỉ hiện ở badge.
 */
const SEARCH_DEBOUNCE_MS = 300
const ROLES: RoleName[] = ['USER', 'OWNER', 'MODERATOR', 'ADMIN']
const uid = useId()

const searchInput = ref('')
const query = ref('')
const role = ref<RoleName | ''>('')
const lockedFilter = ref<'' | 'true' | 'false'>('')
const selectedId = ref<number | null>(null)

let debounce: ReturnType<typeof setTimeout> | undefined
watch(searchInput, (value) => {
  clearTimeout(debounce)
  debounce = setTimeout(() => (query.value = value.trim()), SEARCH_DEBOUNCE_MS)
})

const filter = computed<AdminUserFilter>(() => ({
  q: query.value || undefined,
  role: role.value || undefined,
  locked: lockedFilter.value === '' ? undefined : lockedFilter.value === 'true',
}))

const users = useInfiniteQuery({
  queryKey: computed(() => ['admin-users', filter.value]),
  queryFn: ({ pageParam }) => adminUsersApi.search(filter.value, pageParam),
  initialPageParam: null as string | null,
  getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
})

const rows = computed<AdminUser[]>(() => users.data.value?.pages.flatMap((p) => p.items) ?? [])
const selected = computed(() => rows.value.find((u) => u.id === selectedId.value) ?? null)
const errorText = computed(() =>
  users.error.value ? problemMessage(toProblem(users.error.value)) : null,
)

/** Cột "Vai trò": vai trò cao nhất, kèm số vai trò khác (ngoài Thành viên). */
function roleText(roles: RoleName[]): string {
  const main = primaryRole(roles)
  const extra = roles.filter((r) => r !== main && r !== 'USER').length
  return extra > 0 ? `${ROLE_LABELS[main]} +${extra}` : ROLE_LABELS[main]
}
</script>

<template>
  <div>
    <div class="ls-pagehead">
      <div>
        <h1>Người dùng &amp; vai trò</h1>
        <p>Tìm tài khoản, gán vai trò kiểm duyệt / quản trị, khoá tài khoản vi phạm.</p>
      </div>
    </div>

    <div class="flex flex-wrap gap-2 pb-4 pt-6" role="search">
      <label class="sr-only" :for="`${uid}-q`">Tìm theo tên hoặc email</label>
      <input
        :id="`${uid}-q`"
        v-model="searchInput"
        type="search"
        class="ls-input w-[280px]"
        placeholder="Tìm theo tên hoặc email"
      />
      <label class="sr-only" :for="`${uid}-role`">Vai trò</label>
      <span class="ls-selectwrap w-[180px]">
        <select :id="`${uid}-role`" v-model="role" class="ls-select">
          <option value="">Mọi vai trò</option>
          <option v-for="r in ROLES" :key="r" :value="r">{{ ROLE_LABELS[r] }}</option>
        </select>
      </span>
      <label class="sr-only" :for="`${uid}-status`">Trạng thái</label>
      <span class="ls-selectwrap w-[180px]">
        <select :id="`${uid}-status`" v-model="lockedFilter" class="ls-select">
          <option value="">Mọi trạng thái</option>
          <option value="false">Hoạt động</option>
          <option value="true">Đã khoá</option>
        </select>
      </span>
    </div>

    <div class="ls-split grid lg:grid-cols-[1fr_300px]">
      <div class="min-w-0">
        <p v-if="errorText" role="alert" class="ls-field__error">✕ {{ errorText }}</p>
        <table class="ls-table">
          <thead>
            <tr>
              <th scope="col">Người dùng</th>
              <th scope="col">Vai trò</th>
              <th scope="col">Trạng thái</th>
              <th scope="col" class="r">Điểm tin cậy</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="users.isPending.value">
              <td colspan="4" class="text-ink-muted">Đang tải…</td>
            </tr>
            <tr v-else-if="!rows.length && !errorText">
              <td colspan="4" class="text-ink-muted">Không có người dùng nào khớp bộ lọc.</td>
            </tr>
            <tr
              v-for="user in rows"
              :key="user.id"
              class="cursor-pointer"
              :aria-selected="user.id === selectedId"
              @click="selectedId = user.id"
            >
              <td>
                <button
                  type="button"
                  class="ls-link cursor-pointer border-0 bg-transparent p-0 text-left"
                  @click.stop="selectedId = user.id"
                >
                  {{ user.displayName }}
                </button>
                <div class="text-caption text-ink-muted">{{ user.email }}</div>
              </td>
              <td class="whitespace-nowrap">{{ roleText(user.roles) }}</td>
              <td>
                <LsBadge :tone="STATUS_BADGE[userStatus(user)].tone">{{
                  STATUS_BADGE[userStatus(user)].text
                }}</LsBadge>
              </td>
              <td class="r num">{{ formatNumber(user.trustScore) }}</td>
            </tr>
          </tbody>
        </table>
        <div class="flex items-center justify-between py-4 text-caption text-ink-muted">
          <span>Đang hiển thị {{ formatNumber(rows.length) }} người dùng</span>
          <LsAdminButton
            v-if="users.hasNextPage.value"
            size="sm"
            :loading="users.isFetchingNextPage.value"
            @click="users.fetchNextPage()"
          >
            Tải thêm
          </LsAdminButton>
        </div>
      </div>

      <aside aria-label="Chi tiết người dùng">
        <UserDetailPanel v-if="selected" :user="selected" />
        <p v-else class="m-0 text-caption text-ink-muted">
          Chọn một người dùng để xem chi tiết và đổi vai trò.
        </p>
      </aside>
    </div>
  </div>
</template>
