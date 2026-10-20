<script setup lang="ts">
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed } from 'vue'

import { bannersApi } from '@/api/banners'
import { problemMessage, toProblem } from '@/api/problem'
import LsAdminButton from '@/components/ui/LsAdminButton.vue'
import { catalogKeys } from '@/composables/catalogQueries'
import type { Banner } from '@/types/api'

import BannerSlotPanel from './BannerSlotPanel.vue'
import { BANNER_SLOTS } from './bannerSlots'

/**
 * Ảnh trang chủ (UC34, FR-43 — mở lại Q4 ngày 2026-10-17, phạm vi tối thiểu): ba ô cố định theo bố cục HomePage, mỗi ô
 * một khối. Không có lịch chạy, link, thứ tự — đúng phạm vi đã chốt. Đổi xong thì bỏ dữ liệu trang chủ đang giữ ở trình
 * duyệt này (backend tự xoá cache dùng chung).
 */
const BANNERS_KEY = ['admin-banners'] as const
const queryClient = useQueryClient()

const banners = useQuery({ queryKey: BANNERS_KEY, queryFn: bannersApi.list })
const errorText = computed(() =>
  banners.error.value ? problemMessage(toProblem(banners.error.value)) : null,
)
const panels = computed(() =>
  BANNER_SLOTS.map((meta) => ({
    ...meta,
    banner: banners.data.value?.find((b) => b.slot === meta.slot) ?? null,
  })),
)

function onChanged(updated: Banner): void {
  queryClient.setQueryData<Banner[]>(BANNERS_KEY, (list) =>
    list?.map((b) => (b.slot === updated.slot ? updated : b)),
  )
  void queryClient.invalidateQueries({ queryKey: catalogKeys.home })
}
</script>

<template>
  <div>
    <div class="ls-pagehead">
      <div>
        <h1>Ảnh trang chủ</h1>
        <p>Thay ảnh minh hoạ ở ba vị trí cố định trên trang chủ. Tiêu đề và nội dung giữ nguyên.</p>
      </div>
    </div>

    <div v-if="errorText" role="alert" class="py-6">
      <p class="ls-field__error">✕ {{ errorText }}</p>
      <LsAdminButton @click="banners.refetch()">Thử lại</LsAdminButton>
    </div>
    <div v-else-if="banners.isPending.value" aria-busy="true" class="py-6">
      <span class="ls-skel h-40 w-full"></span>
    </div>
    <template v-else>
      <template v-for="panel in panels" :key="panel.slot">
        <BannerSlotPanel
          v-if="panel.banner"
          :banner="panel.banner"
          :title="panel.title"
          :description="panel.description"
          :crop="panel.crop"
          @changed="onChanged"
        />
      </template>
    </template>
  </div>
</template>
