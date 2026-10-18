<script setup lang="ts">
import { computed } from 'vue'

import FlatPicture from '@/components/ui/FlatPicture.vue'
import type { HomeBanner } from '@/types/api'

/**
 * Ảnh một ô trang chủ (UC34): ảnh quản trị viên đặt nếu có, không thì hình minh hoạ phẳng như trước. `srcset` 3 cỡ để
 * trình duyệt chọn theo khung. `scrim` (hero — chữ và ô tìm kiếm đè lên ảnh): phủ một lớp tối **đều** 65% (không
 * gradient — README), màu `on-accent` cố định tối ở cả hai theme. Với ảnh trắng tinh, chữ `on-olive` vẫn ≈ 4,7:1 (đạt
 * WCAG AA cho chữ thường) — quản trị viên đặt ảnh nào cũng không phá tương phản.
 */
const props = withDefaults(
  defineProps<{ banner?: HomeBanner | null; sizes?: string; scrim?: boolean; eager?: boolean }>(),
  { banner: null, sizes: '100vw', scrim: false, eager: false },
)

const image = computed(() => props.banner?.image ?? null)
const srcset = computed(() => {
  const p = image.value
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
  <div v-if="image" class="banner-pic">
    <img
      :src="image.mediumUrl ?? image.largeUrl ?? undefined"
      :srcset="srcset"
      :sizes="sizes"
      :alt="banner?.altText ?? ''"
      :loading="eager ? 'eager' : 'lazy'"
      decoding="async"
    />
    <span v-if="scrim" class="banner-pic__scrim" aria-hidden="true"></span>
  </div>
  <FlatPicture v-else />
</template>

<style scoped>
.banner-pic {
  position: relative;
  overflow: hidden;
}
.banner-pic img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.banner-pic__scrim {
  position: absolute;
  inset: 0;
  background: color-mix(in srgb, var(--on-accent) 65%, transparent);
}
</style>
