<script setup lang="ts">
import { onBeforeUnmount, ref, useTemplateRef } from 'vue'
import { Cropper } from 'vue-advanced-cropper'
import 'vue-advanced-cropper/dist/style.css'

import LsButton from '@/components/ui/LsButton.vue'
import { MAX_EDGE, canvasToJpeg, jpegName, type CropPreset } from '@/utils/images'

/**
 * Cắt ảnh trước khi tải lên (FR-19 — "crop phía client", thư viện vue-advanced-cropper theo plan §2). Không bắt buộc:
 * người dùng mở khi muốn. Kết quả xuất JPEG, cạnh dài ≤ {@link MAX_EDGE} — cùng giới hạn với ảnh không cắt. Gọi
 * `crop(file)`: trả ảnh đã cắt, hoặc `null` khi đóng mà không áp dụng. `preset` (ảnh trang chủ — UC34) thêm tỷ lệ
 * khớp khung hiển thị và chọn sẵn tỷ lệ đó.
 */
const dialog = useTemplateRef<HTMLDialogElement>('dialog')
const cropper = useTemplateRef<InstanceType<typeof Cropper>>('cropper')
const source = ref<string | null>(null)
const fileName = ref('anh.jpg')
const aspect = ref<number | undefined>(undefined)
const applying = ref(false)
let settle: ((file: File | null) => void) | null = null

const BASE_ASPECTS: { label: string; value: number | undefined }[] = [
  { label: 'Tự do', value: undefined },
  { label: '4:3', value: 4 / 3 },
  { label: 'Vuông', value: 1 },
]
const aspects = ref(BASE_ASPECTS)

function crop(file: File, preset?: CropPreset): Promise<File | null> {
  release()
  source.value = URL.createObjectURL(file)
  fileName.value = file.name
  aspects.value = preset
    ? [{ label: preset.label, value: preset.aspect }, ...BASE_ASPECTS]
    : BASE_ASPECTS
  aspect.value = preset?.aspect
  dialog.value?.showModal()
  return new Promise((resolve) => {
    settle = resolve
  })
}

defineExpose({ crop })

function finish(result: File | null): void {
  settle?.(result)
  settle = null
  dialog.value?.close()
}

async function apply(): Promise<void> {
  const canvas = cropper.value?.getResult().canvas
  if (!canvas) {
    finish(null)
    return
  }
  applying.value = true
  try {
    const blob = await canvasToJpeg(canvas)
    finish(new File([blob], jpegName(fileName.value), { type: 'image/jpeg' }))
  } finally {
    applying.value = false
  }
}

function release(): void {
  if (source.value) {
    URL.revokeObjectURL(source.value)
    source.value = null
  }
}

function onClose(): void {
  // Esc / bấm ra ngoài: coi như huỷ
  settle?.(null)
  settle = null
  release()
}

onBeforeUnmount(release)
</script>

<template>
  <dialog
    ref="dialog"
    class="ls-pop m-auto w-[min(820px,calc(100vw-32px))] p-0 text-ink backdrop:bg-ink/60"
    aria-label="Cắt ảnh"
    @close="onClose"
  >
    <div class="flex items-center justify-between gap-4 border-b border-line p-4">
      <h2 class="m-0 text-title">Cắt ảnh</h2>
      <LsButton variant="ghost" size="sm" @click="finish(null)">Huỷ</LsButton>
    </div>
    <Cropper
      v-if="source"
      ref="cropper"
      class="ls-crop"
      :src="source"
      :stencil-props="{ aspectRatio: aspect }"
      :canvas="{ maxWidth: MAX_EDGE, maxHeight: MAX_EDGE }"
    />
    <div class="flex flex-wrap items-center justify-between gap-3 p-4">
      <div class="flex flex-wrap gap-2" role="group" aria-label="Tỷ lệ khung">
        <button
          v-for="option in aspects"
          :key="option.label"
          type="button"
          class="ls-fchip"
          :aria-pressed="aspect === option.value"
          @click="aspect = option.value"
        >
          {{ option.label }}
        </button>
        <button type="button" class="ls-fchip" @click="cropper?.rotate(90)">Xoay 90°</button>
      </div>
      <LsButton :loading="applying" @click="apply">Dùng ảnh này</LsButton>
    </div>
  </dialog>
</template>
