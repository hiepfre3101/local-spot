<script setup lang="ts">
import { computed } from 'vue'

import FlatPicture from '@/components/ui/FlatPicture.vue'
import type { Photo } from '@/types/api'

/**
 * Ảnh địa điểm / review: ảnh thật khi đã xử lý xong (`READY`), `srcset` 3 cỡ để trình duyệt chọn theo khung; chưa có ảnh
 * thì minh hoạ phẳng giữ chỗ (README: không phủ gradient). `alt` rỗng khi ảnh chỉ trang trí cạnh tên đã có chữ.
 */
const props = withDefaults(
  defineProps<{ photo: Photo | null; alt?: string; sizes?: string; eager?: boolean }>(),
  { alt: '', sizes: '(min-width: 768px) 33vw, 100vw', eager: false },
)

const ready = computed(() =>
  props.photo?.status === 'READY' && props.photo.mediumUrl ? props.photo : null,
)
const srcset = computed(() => {
  const p = ready.value
  if (!p) return undefined
  return [
    p.thumbUrl && `${p.thumbUrl} 320w`,
    p.mediumUrl && `${p.mediumUrl} 960w`,
    p.largeUrl && `${p.largeUrl} 1920w`,
  ]
    .filter(Boolean)
    .join(', ')
})
</script>

<template>
  <div v-if="ready" class="ls-pic">
    <img
      :src="ready.mediumUrl!"
      :srcset="srcset"
      :sizes="sizes"
      :alt="alt"
      :loading="eager ? 'eager' : 'lazy'"
      decoding="async"
    />
    <slot />
  </div>
  <FlatPicture v-else>
    <slot />
  </FlatPicture>
</template>
