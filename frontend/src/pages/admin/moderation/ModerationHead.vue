<script setup lang="ts">
import { RouterLink } from 'vue-router'

import type { ModerationItemType } from '@/types/api'

import { TYPE_LABELS } from './moderation'

/**
 * Đầu màn xử lý (AdminForm): đường dẫn "Kiểm duyệt / <loại> / #id", tiêu đề, dòng meta (trạng thái, người gửi, thời
 * gian) và hai hành động ở bên phải — từ chối (`danger`) / chấp nhận (`primary`), một nút chính mỗi trang.
 */
defineProps<{ type: ModerationItemType; refLabel: string; title: string }>()
</script>

<template>
  <div class="ls-pagehead flex-wrap">
    <div class="min-w-0">
      <p class="mt-0! mb-1!">
        Kiểm duyệt /
        <RouterLink class="ls-link" :to="{ name: 'admin-queue', query: { type } }">{{
          TYPE_LABELS[type]
        }}</RouterLink>
        / {{ refLabel }}
      </p>
      <h1 class="break-words">{{ title }}</h1>
      <div class="ls-meta-row"><slot name="meta" /></div>
    </div>
    <div class="flex flex-wrap gap-2"><slot name="actions" /></div>
  </div>
</template>
