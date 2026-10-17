<script setup lang="ts">
import { computed, ref, useId, useTemplateRef } from 'vue'

import PhotoCropDialog from '@/components/review/PhotoCropDialog.vue'
import { draftOf, releaseDraft, type DraftPhoto } from '@/components/review/draftPhoto'
import type { Photo } from '@/types/api'
import { ImageDecodeError, prepareImage } from '@/utils/images'
import { REVIEW_PHOTOS_MAX } from '@/validation/review'

/**
 * Ảnh đính kèm review (FR-19): chọn nhiều ảnh, xem trước, cắt (tùy chọn), bỏ. Ảnh chọn được chuẩn bị ngay ở trình duyệt
 * (`prepareImage` — JPEG / PNG ≤ 5 MB) nên lỗi định dạng báo trước khi gửi. Màn sửa có thêm ảnh đã tải (`existing`):
 * bỏ ảnh đó chỉ đánh dấu, trang gọi API xóa khi lưu. Tổng ảnh cũ + mới ≤ 10, như backend.
 */
const props = defineProps<{ existing?: Photo[]; disabled?: boolean }>()
const drafts = defineModel<DraftPhoto[]>({ required: true })
const emit = defineEmits<{ removeExisting: [photoId: number] }>()

const id = useId()
const cropDialog = useTemplateRef<InstanceType<typeof PhotoCropDialog>>('cropDialog')
const error = ref<string | null>(null)
const preparing = ref(false)

const existingCount = computed(() => props.existing?.length ?? 0)
const remaining = computed(() => REVIEW_PHOTOS_MAX - existingCount.value - drafts.value.length)

async function onFiles(event: Event): Promise<void> {
  const target = event.target as HTMLInputElement
  const files = Array.from(target.files ?? [])
  target.value = '' // chọn lại cùng tệp vẫn phát sự kiện
  error.value = null
  if (files.length > remaining.value) {
    error.value = `Tối đa ${REVIEW_PHOTOS_MAX} ảnh — chỉ thêm được ${Math.max(remaining.value, 0)} ảnh nữa.`
  }
  preparing.value = true
  const added: DraftPhoto[] = []
  const failed: string[] = []
  for (const file of files.slice(0, Math.max(remaining.value, 0))) {
    try {
      added.push(draftOf(await prepareImage(file)))
    } catch (cause) {
      failed.push(
        cause instanceof ImageDecodeError ? cause.message : `Không xử lý được "${file.name}".`,
      )
    }
  }
  preparing.value = false
  drafts.value = [...drafts.value, ...added]
  if (failed.length) {
    error.value = failed.join(' ')
  }
}

async function crop(draft: DraftPhoto): Promise<void> {
  const cropped = await cropDialog.value?.crop(draft.file)
  if (!cropped) return
  const replacement = draftOf(cropped)
  drafts.value = drafts.value.map((d) => (d.key === draft.key ? replacement : d))
  releaseDraft(draft)
}

function remove(draft: DraftPhoto): void {
  drafts.value = drafts.value.filter((d) => d.key !== draft.key)
  releaseDraft(draft)
}
</script>

<template>
  <div class="ls-field">
    <span :id="`${id}-label`" class="ls-field__label"
      >Ảnh (tuỳ chọn, tối đa {{ REVIEW_PHOTOS_MAX }})</span
    >
    <ul class="ls-photos" :aria-labelledby="`${id}-label`">
      <li v-for="photo in existing" :key="`photo-${photo.id}`">
        <img
          v-if="photo.thumbUrl"
          class="ls-photos__img"
          :src="photo.thumbUrl"
          alt="Ảnh đã đăng của bạn"
        />
        <span v-else class="ls-photos__img grid place-items-center text-caption text-ink-muted">
          {{ photo.status === 'FAILED' ? 'Ảnh lỗi' : 'Đang xử lý' }}
        </span>
        <div class="ls-photos__tools">
          <button type="button" :disabled="disabled" @click="emit('removeExisting', photo.id)">
            Bỏ
          </button>
        </div>
      </li>
      <li v-for="(draft, index) in drafts" :key="draft.key">
        <img class="ls-photos__img" :src="draft.url" :alt="`Ảnh mới ${index + 1}`" />
        <div class="ls-photos__tools">
          <button type="button" :disabled="disabled" @click="crop(draft)">Cắt</button>
          <button type="button" :disabled="disabled" @click="remove(draft)">Bỏ</button>
        </div>
      </li>
      <li v-if="remaining > 0">
        <label class="ls-photos__add" :aria-busy="preparing || undefined">
          <span aria-hidden="true">+</span>
          {{ preparing ? 'Đang xử lý…' : 'Thêm ảnh' }}
          <input
            type="file"
            class="sr-only"
            accept="image/jpeg,image/png,image/webp,image/heic,image/heif"
            multiple
            :disabled="disabled || preparing"
            @change="onFiles"
          />
        </label>
      </li>
    </ul>
    <span class="ls-field__hint">JPG, PNG ≤ 5 MB. Ảnh định dạng khác được chuyển sang JPG.</span>
    <span v-if="error" role="alert" class="ls-field__error">✕ {{ error }}</span>
    <PhotoCropDialog ref="cropDialog" />
  </div>
</template>
