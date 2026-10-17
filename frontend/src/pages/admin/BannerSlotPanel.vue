<script setup lang="ts">
import { computed, ref, useId, useTemplateRef, watch } from 'vue'

import { bannersApi } from '@/api/banners'
import { problemMessage, toProblem } from '@/api/problem'
import PhotoCropDialog from '@/components/review/PhotoCropDialog.vue'
import FlatPicture from '@/components/ui/FlatPicture.vue'
import LsAdminButton from '@/components/ui/LsAdminButton.vue'
import LsBadge from '@/components/ui/LsBadge.vue'
import type { Banner } from '@/types/api'
import { formatDateTime } from '@/utils/format'
import { ImageDecodeError, prepareImage, type CropPreset } from '@/utils/images'

/**
 * Một ô ảnh trang chủ (UC34): xem trước đúng tỷ lệ khung, thay ảnh (chọn → cắt theo khung gợi ý → tải), mô tả ảnh,
 * bật / tắt, gỡ (xác nhận tại chỗ). Mỗi thao tác trả về ô mới nhất — trang cha cập nhật cache, không tải lại cả danh
 * sách. Trạng thái luôn có ký hiệu + chữ (Badge README).
 */
const props = defineProps<{
  banner: Banner
  title: string
  description: string
  crop: CropPreset
}>()
const emit = defineEmits<{ changed: [banner: Banner] }>()

const id = useId()
const cropDialog = useTemplateRef<InstanceType<typeof PhotoCropDialog>>('cropDialog')
const altText = ref(props.banner.altText)
const busy = ref<'upload' | 'alt' | 'toggle' | 'remove' | null>(null)
const error = ref<string | null>(null)
const confirmingRemove = ref(false)

watch(
  () => props.banner,
  (banner) => {
    altText.value = banner.altText
  },
)

const hasImage = computed(() => props.banner.image !== null)
const status = computed(() => {
  if (!hasImage.value)
    return { tone: 'neutral', text: '○ Chưa có ảnh — đang dùng hình minh hoạ' } as const
  return props.banner.active
    ? ({ tone: 'success', text: '✓ Đang hiện trên trang chủ' } as const)
    : ({ tone: 'warning', text: '● Đã tắt — trang chủ dùng hình minh hoạ' } as const)
})
const altChanged = computed(() => altText.value.trim() !== props.banner.altText)

async function run(kind: NonNullable<typeof busy.value>, action: () => Promise<Banner | void>) {
  busy.value = kind
  error.value = null
  try {
    const result = await action()
    if (result) emit('changed', result)
  } catch (cause) {
    error.value = problemMessage(toProblem(cause))
  } finally {
    busy.value = null
  }
}

async function onFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  error.value = null
  let prepared: File
  try {
    prepared = await prepareImage(file)
  } catch (cause) {
    error.value = cause instanceof ImageDecodeError ? cause.message : 'Không xử lý được ảnh này.'
    return
  }
  // Cắt theo khung hiển thị; huỷ ở bước cắt = không đổi gì
  const cropped = await cropDialog.value?.crop(prepared, props.crop)
  if (!cropped) return
  const alt = altText.value.trim()
  await run('upload', () =>
    bannersApi.replaceImage(props.banner.slot, cropped, cropped.name, alt || undefined),
  )
}

function saveAlt(): Promise<void> {
  return run('alt', () =>
    bannersApi.update(props.banner.slot, {
      altText: altText.value.trim(),
      version: props.banner.version!,
    }),
  )
}

function toggle(): Promise<void> {
  return run('toggle', () =>
    bannersApi.update(props.banner.slot, {
      active: !props.banner.active,
      version: props.banner.version!,
    }),
  )
}

async function remove(): Promise<void> {
  await run('remove', async () => {
    await bannersApi.remove(props.banner.slot)
    return {
      ...props.banner,
      id: null,
      image: null,
      altText: '',
      active: false,
      version: null,
      updatedAt: null,
      updatedBy: null,
    }
  })
  confirmingRemove.value = false
}
</script>

<template>
  <section
    class="grid gap-6 border-t border-line py-6 md:grid-cols-[320px_1fr]"
    :aria-labelledby="`${id}-title`"
  >
    <div class="overflow-hidden" :style="{ aspectRatio: String(crop.aspect) }">
      <img
        v-if="banner.image"
        class="h-full w-full object-cover"
        :src="banner.image.mediumUrl ?? undefined"
        :alt="`Ảnh hiện tại — ${title}`"
      />
      <FlatPicture v-else class="h-full" />
    </div>

    <div class="flex min-w-0 flex-col gap-4">
      <div>
        <h2 :id="`${id}-title`" class="m-0 text-title">{{ title }}</h2>
        <p class="mt-1 mb-2 text-caption text-ink-muted">{{ description }}</p>
        <LsBadge :tone="status.tone">{{ status.text }}</LsBadge>
        <p
          v-if="banner.updatedBy && banner.updatedAt"
          class="mt-2 mb-0 text-caption text-ink-muted"
        >
          Cập nhật bởi {{ banner.updatedBy.displayName }} lúc {{ formatDateTime(banner.updatedAt) }}
        </p>
      </div>

      <div class="ls-field max-w-[480px]">
        <label :for="`${id}-alt`">Mô tả ảnh</label>
        <input
          :id="`${id}-alt`"
          v-model="altText"
          class="ls-input"
          maxlength="200"
          :aria-describedby="`${id}-alt-hint`"
        />
        <span :id="`${id}-alt-hint`" class="ls-field__hint"
          >Cho trình đọc màn hình. Để trống nếu ảnh chỉ để trang trí.</span
        >
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <label
          :class="['ls-abtn', 'ls-abtn--primary', busy && 'pointer-events-none opacity-60']"
          :aria-busy="busy === 'upload' || undefined"
        >
          {{ busy === 'upload' ? 'Đang tải ảnh…' : hasImage ? 'Thay ảnh' : 'Tải ảnh lên' }}
          <input
            type="file"
            class="sr-only"
            accept="image/jpeg,image/png,image/webp,image/heic,image/heif"
            :disabled="busy !== null"
            @change="onFile"
          />
        </label>
        <template v-if="hasImage">
          <LsAdminButton :disabled="!altChanged" :loading="busy === 'alt'" @click="saveAlt"
            >Lưu mô tả</LsAdminButton
          >
          <LsAdminButton :loading="busy === 'toggle'" @click="toggle">{{
            banner.active ? 'Tắt ảnh' : 'Bật ảnh'
          }}</LsAdminButton>
          <LsAdminButton v-if="!confirmingRemove" variant="danger" @click="confirmingRemove = true"
            >Gỡ ảnh</LsAdminButton
          >
        </template>
      </div>

      <div v-if="confirmingRemove" role="alert" class="flex flex-wrap items-center gap-2">
        <span>Gỡ ảnh này? Trang chủ sẽ về hình minh hoạ, ảnh bị xoá khỏi kho.</span>
        <LsAdminButton variant="danger" :loading="busy === 'remove'" @click="remove"
          >Gỡ ảnh</LsAdminButton
        >
        <LsAdminButton @click="confirmingRemove = false">Giữ lại</LsAdminButton>
      </div>

      <p v-if="error" role="alert" class="ls-field__error m-0">✕ {{ error }}</p>
      <p class="m-0 text-caption text-ink-muted">
        JPG, PNG ≤ 5 MB (định dạng khác được chuyển sang JPG). Ảnh được cắt theo khung trước khi
        tải.
      </p>
    </div>
    <PhotoCropDialog ref="cropDialog" />
  </section>
</template>
