<script setup lang="ts">
import { computed, ref, useId, useTemplateRef } from 'vue'

import { collectionsApi, type CollectionInput } from '@/api/collections'
import { problemMessage, toProblem } from '@/api/problem'
import LsButton from '@/components/ui/LsButton.vue'
import type { CollectionDetail } from '@/types/api'

/**
 * Tạo / sửa bộ sưu tập (UC18) — tên (bắt buộc, ≤ 150), mô tả (≤ 1000), công khai (FR-26: bật / tắt từng bộ, mặc định
 * riêng tư). Gọi `open()` để tạo, `open(collection)` để sửa; lưu xong phát `saved` với bộ đã lưu.
 */
const NAME_MAX = 150
const DESCRIPTION_MAX = 1000

const emit = defineEmits<{ saved: [collection: CollectionDetail] }>()

const id = useId()
const dialog = useTemplateRef<HTMLDialogElement>('dialog')
const editing = ref<CollectionDetail | null>(null)
const name = ref('')
const description = ref('')
const isPublic = ref(false)
const error = ref<string | null>(null)
const nameError = ref<string | null>(null)
const saving = ref(false)

const title = computed(() => (editing.value ? 'Sửa bộ sưu tập' : 'Tạo bộ sưu tập'))
/** Đang công khai mà tắt → link cũ chết vĩnh viễn (chốt 2026-10-12): báo trước khi lưu. */
const revokesLink = computed(() => !!editing.value?.isPublic && !isPublic.value)

function open(collection: CollectionDetail | null = null): void {
  editing.value = collection
  name.value = collection?.name ?? ''
  description.value = collection?.description ?? ''
  isPublic.value = collection?.isPublic ?? false
  error.value = null
  nameError.value = null
  dialog.value?.showModal()
}

defineExpose({ open })

function close(): void {
  dialog.value?.close()
}

async function submit(): Promise<void> {
  const input: CollectionInput = {
    name: name.value.trim(),
    description: description.value.trim() || null,
    isPublic: isPublic.value,
  }
  nameError.value = input.name ? null : 'Nhập tên bộ sưu tập.'
  if (nameError.value) return
  error.value = null
  saving.value = true
  try {
    const saved = editing.value
      ? await collectionsApi.update(editing.value.id, input)
      : await collectionsApi.create(input)
    close()
    emit('saved', saved)
  } catch (cause) {
    error.value = problemMessage(toProblem(cause))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <dialog
    ref="dialog"
    class="ls-pop m-auto w-[min(520px,calc(100vw-32px))] p-0 text-ink backdrop:bg-ink/60"
    :aria-labelledby="`${id}-title`"
  >
    <form class="ls-pub flex flex-col gap-5 p-6" novalidate @submit.prevent="submit">
      <h2 :id="`${id}-title`" class="m-0 text-title">{{ title }}</h2>
      <div class="ls-field">
        <label :for="`${id}-name`">Tên *</label>
        <input
          :id="`${id}-name`"
          v-model="name"
          class="ls-input"
          :maxlength="NAME_MAX"
          placeholder="Vd. Quán ăn Hà Nội nên thử"
          :aria-invalid="!!nameError"
          :aria-describedby="nameError ? `${id}-name-error` : undefined"
          autocomplete="off"
        />
        <p v-if="nameError" :id="`${id}-name-error`" class="ls-field__error m-0">
          ✕ {{ nameError }}
        </p>
      </div>
      <div class="ls-field">
        <label :for="`${id}-description`">Mô tả (tuỳ chọn)</label>
        <textarea
          :id="`${id}-description`"
          v-model="description"
          class="ls-textarea"
          rows="3"
          :maxlength="DESCRIPTION_MAX"
        ></textarea>
      </div>
      <div>
        <label class="ls-check">
          <input v-model="isPublic" type="checkbox" />
          Công khai — hiện trên trang cá nhân và chia sẻ được bằng link
        </label>
        <p v-if="revokesLink" class="m-0 mt-2 text-caption text-ink-muted" role="status">
          ● Chuyển về riêng tư: link đã chia sẻ sẽ ngừng hoạt động. Công khai lại sẽ có link mới.
        </p>
      </div>
      <p v-if="error" role="alert" class="ls-field__error m-0">✕ {{ error }}</p>
      <div class="flex flex-wrap justify-end gap-3">
        <LsButton variant="ghost" @click="close">Huỷ</LsButton>
        <LsButton type="submit" :loading="saving">{{
          editing ? 'Lưu thay đổi' : 'Tạo bộ sưu tập'
        }}</LsButton>
      </div>
    </form>
  </dialog>
</template>
