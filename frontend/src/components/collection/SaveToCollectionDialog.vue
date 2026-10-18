<script setup lang="ts">
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, useId, useTemplateRef } from 'vue'
import { RouterLink } from 'vue-router'

import { collectionsApi } from '@/api/collections'
import { problemMessage, toProblem } from '@/api/problem'
import LsButton from '@/components/ui/LsButton.vue'
import { useNotify } from '@/plugins/toast'
import { useAuthStore } from '@/stores/auth'
import type { CollectionDetail, CollectionSummary } from '@/types/api'
import { formatNumber } from '@/utils/format'

import CollectionFormDialog from './CollectionFormDialog.vue'
import { collectionKeys, invalidateCollections } from './collectionQueries'

/**
 * Lưu địa điểm vào bộ sưu tập (UC18, P03): danh sách bộ của tôi, tick = địa điểm đã có trong bộ (`containsPlace`).
 * Tick / bỏ tick gọi API ngay (PUT / DELETE idempotent) — không có nút "Lưu" thứ hai. "+ Tạo bộ sưu tập mới" tạo bộ rồi
 * thêm luôn địa điểm này. Trang gọi `open()` sau khi đã kiểm đăng nhập.
 */
const props = defineProps<{ placeId: number; placeName: string }>()

const id = useId()
const auth = useAuthStore()
const notify = useNotify()
const queryClient = useQueryClient()
const dialog = useTemplateRef<HTMLDialogElement>('dialog')
const form = useTemplateRef<InstanceType<typeof CollectionFormDialog>>('form')
const busy = ref<number | null>(null)
const error = ref<string | null>(null)

const key = computed(() => collectionKeys.forPlace(props.placeId))
const collections = useQuery({
  queryKey: key,
  queryFn: () => collectionsApi.mine(props.placeId),
  enabled: computed(() => auth.isAuthenticated),
})

function open(): void {
  error.value = null
  void collections.refetch()
  dialog.value?.showModal()
}

defineExpose({ open })

function setContains(collectionId: number, contains: boolean): void {
  queryClient.setQueryData<CollectionSummary[]>(key.value, (old) =>
    old?.map((c) =>
      c.id === collectionId
        ? {
            ...c,
            containsPlace: contains,
            placeCount: Math.max(0, c.placeCount + (contains ? 1 : -1)),
          }
        : c,
    ),
  )
}

async function toggle(collection: CollectionSummary): Promise<void> {
  const next = !collection.containsPlace
  busy.value = collection.id
  error.value = null
  setContains(collection.id, next)
  try {
    await (next
      ? collectionsApi.addPlace(collection.id, props.placeId)
      : collectionsApi.removePlace(collection.id, props.placeId))
  } catch (cause) {
    setContains(collection.id, !next)
    error.value = problemMessage(toProblem(cause))
  } finally {
    busy.value = null
    invalidateCollections(queryClient, auth.user?.id)
  }
}

async function created(collection: CollectionDetail): Promise<void> {
  try {
    await collectionsApi.addPlace(collection.id, props.placeId)
    notify.success(`Đã lưu vào “${collection.name}”.`)
  } catch (cause) {
    error.value = problemMessage(toProblem(cause))
  } finally {
    invalidateCollections(queryClient, auth.user?.id)
  }
}

function close(): void {
  dialog.value?.close()
}
</script>

<template>
  <dialog
    ref="dialog"
    class="ls-pop m-auto w-[min(480px,calc(100vw-32px))] p-0 text-ink backdrop:bg-ink/60"
    :aria-labelledby="`${id}-title`"
  >
    <div class="ls-pub flex flex-col gap-5 p-6">
      <h2 :id="`${id}-title`" class="m-0 text-title">Lưu “{{ placeName }}”</h2>

      <p v-if="collections.isError.value" role="alert" class="m-0">
        ✕ Chưa tải được bộ sưu tập của bạn.
      </p>
      <p v-else-if="collections.isPending.value" aria-busy="true" class="m-0 text-ink-muted">
        Đang tải…
      </p>
      <p v-else-if="!collections.data.value?.length" class="m-0 text-ink-muted">
        Bạn chưa có bộ sưu tập nào — tạo bộ đầu tiên để lưu địa điểm này.
      </p>
      <fieldset v-else class="m-0 flex max-h-[50vh] flex-col gap-1 overflow-y-auto border-0 p-0">
        <legend class="sr-only">Bộ sưu tập của bạn</legend>
        <div
          v-for="c in collections.data.value"
          :key="c.id"
          class="flex items-center justify-between gap-3 border-b border-line py-2"
        >
          <label class="ls-check min-w-0">
            <input
              type="checkbox"
              :checked="!!c.containsPlace"
              :disabled="busy === c.id"
              @change="toggle(c)"
            />
            <span class="truncate">{{ c.name }}</span>
          </label>
          <span class="shrink-0 text-caption text-ink-muted">
            {{ formatNumber(c.placeCount) }} địa điểm · {{ c.isPublic ? 'Công khai' : 'Riêng tư' }}
          </span>
        </div>
      </fieldset>

      <p v-if="error" role="alert" class="ls-field__error m-0">✕ {{ error }}</p>

      <div class="flex flex-wrap items-center justify-between gap-3">
        <LsButton variant="ghost" @click="form?.open()">+ Tạo bộ sưu tập mới</LsButton>
        <div class="flex items-center gap-3">
          <RouterLink
            v-if="auth.user"
            class="ls-link text-caption"
            :to="{ name: 'profile', params: { id: auth.user.id }, query: { tab: 'collections' } }"
            >Bộ sưu tập của tôi</RouterLink
          >
          <LsButton @click="close">Xong</LsButton>
        </div>
      </div>
    </div>
    <CollectionFormDialog ref="form" @saved="created" />
  </dialog>
</template>
