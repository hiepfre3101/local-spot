<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { moderationApi } from '@/api/moderation'

/**
 * Một file minh chứng của yêu cầu sở hữu. File nằm trong bucket riêng tư: lấy link tạm 5 phút qua API (access token chỉ
 * gửi được bằng header — `<img src>` trỏ thẳng vào API không xác thực được). Ảnh lỗi do link hết hạn → ký link mới một
 * lần rồi thôi (tránh vòng lặp khi file thật sự hỏng). Mở ảnh gốc ở tab mới để phóng to.
 */
const props = defineProps<{ claimId: number; index: number }>()

const url = ref<string | null>(null)
const failed = ref(false)
let renewed = false

async function load(): Promise<void> {
  try {
    url.value = (await moderationApi.evidence(props.claimId, props.index)).url
  } catch {
    failed.value = true
  }
}

function onError(): void {
  if (renewed) {
    failed.value = true
    return
  }
  renewed = true
  void load()
}

onMounted(load)
</script>

<template>
  <figure class="ls-thumb m-0">
    <a v-if="url && !failed" :href="url" target="_blank" rel="noopener noreferrer">
      <img :src="url" :alt="`Minh chứng ${index + 1}`" @error="onError" />
    </a>
    <div v-else class="grid place-items-center text-caption">
      {{ failed ? '✕ Không mở được file' : 'Đang tải…' }}
    </div>
    <figcaption>Minh chứng {{ index + 1 }}</figcaption>
  </figure>
</template>
