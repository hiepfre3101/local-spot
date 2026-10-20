<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import { ownerApi } from '@/api/owner'
import { problemMessage, toProblem } from '@/api/problem'
import LsButton from '@/components/ui/LsButton.vue'
import type { OwnerReply, Review } from '@/types/api'
import { formatDate } from '@/utils/format'

/**
 * Phản hồi công khai của chủ dưới một đánh giá (UC25, FR-33): chưa có → ô viết + "Gửi phản hồi"; đã có → nội dung kèm
 * Sửa / Xoá (xoá có xác nhận). Một PUT tạo hoặc sửa; phát `changed` với phản hồi mới (hoặc `null` khi xoá) để trang cập
 * nhật danh sách "Cần phản hồi".
 */
const props = defineProps<{ review: Review }>()
const emit = defineEmits<{ changed: [reply: OwnerReply | null] }>()

const CONTENT_MAX = 2000

const id = useId()
const reply = ref<OwnerReply | null>(props.review.ownerReply)
const editing = ref(reply.value === null)
const draft = ref(reply.value?.content ?? '')
const error = ref<string | null>(null)
const saving = ref(false)
const confirmingDelete = ref(false)

const remaining = computed(() => CONTENT_MAX - draft.value.length)

function edit(): void {
  draft.value = reply.value?.content ?? ''
  error.value = null
  editing.value = true
}

function cancel(): void {
  editing.value = reply.value === null
  draft.value = reply.value?.content ?? ''
  error.value = null
}

async function save(): Promise<void> {
  const content = draft.value.trim()
  if (!content) {
    error.value = 'Nhập nội dung phản hồi.'
    return
  }
  error.value = null
  saving.value = true
  try {
    reply.value = await ownerApi.reply(props.review.id, content)
    editing.value = false
    emit('changed', reply.value)
  } catch (cause) {
    error.value = problemMessage(toProblem(cause))
  } finally {
    saving.value = false
  }
}

async function remove(): Promise<void> {
  saving.value = true
  try {
    await ownerApi.deleteReply(props.review.id)
    reply.value = null
    draft.value = ''
    editing.value = true
    confirmingDelete.value = false
    emit('changed', null)
  } catch (cause) {
    error.value = problemMessage(toProblem(cause))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <div v-if="reply && !editing" class="ls-reply">
      <p class="m-0 text-caption font-semibold">
        Phản hồi của bạn · {{ formatDate(reply.updatedAt) }}
      </p>
      <p class="m-0 mt-1 whitespace-pre-line">{{ reply.content }}</p>
      <div class="mt-2 flex flex-wrap items-center gap-4">
        <button type="button" class="ls-act" @click="edit">Sửa</button>
        <button
          v-if="!confirmingDelete"
          type="button"
          class="ls-act"
          @click="confirmingDelete = true"
        >
          Xoá
        </button>
        <span v-else role="alert" class="flex items-center gap-2 text-caption">
          Xoá phản hồi này?
          <button type="button" class="ls-act" :disabled="saving" @click="remove">Xoá</button>
          <button type="button" class="ls-act" @click="confirmingDelete = false">Giữ lại</button>
        </span>
      </div>
    </div>

    <form v-else class="flex flex-col gap-2" novalidate @submit.prevent="save">
      <label class="sr-only" :for="`${id}-reply`"
        >Phản hồi công khai cho {{ review.author.displayName }}</label
      >
      <textarea
        :id="`${id}-reply`"
        v-model="draft"
        class="ls-textarea min-h-[80px]"
        rows="3"
        :maxlength="CONTENT_MAX"
        placeholder="Viết phản hồi công khai…"
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="`${id}-hint`"
      ></textarea>
      <span :id="`${id}-hint`" class="text-caption text-ink-muted"
        >Mọi người đều thấy phản hồi này · còn {{ remaining }} ký tự</span
      >
      <p v-if="error" role="alert" class="ls-field__error m-0">✕ {{ error }}</p>
      <div class="flex flex-wrap gap-2">
        <LsButton type="submit" size="sm" :loading="saving">{{
          reply ? 'Lưu phản hồi' : 'Gửi phản hồi'
        }}</LsButton>
        <LsButton v-if="reply" size="sm" variant="ghost" @click="cancel">Huỷ</LsButton>
      </div>
    </form>
  </div>
</template>
