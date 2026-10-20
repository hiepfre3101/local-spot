<script setup lang="ts">
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { isAxiosError } from 'axios'
import { computed, ref, useId, useTemplateRef, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { collectionsApi } from '@/api/collections'
import { problemMessage, toProblem } from '@/api/problem'
import CollectionFormDialog from '@/components/collection/CollectionFormDialog.vue'
import { collectionKeys, invalidateCollections } from '@/components/collection/collectionQueries'
import SpotCard from '@/components/place/SpotCard.vue'
import LsBadge from '@/components/ui/LsBadge.vue'
import LsButton from '@/components/ui/LsButton.vue'
import NotFoundPage from '@/pages/errors/NotFoundPage.vue'
import { useNotify } from '@/plugins/toast'
import { useAuthStore } from '@/stores/auth'
import type { CollectionDetail, PlaceSummary } from '@/types/api'
import { formatNumber } from '@/utils/format'

/**
 * Bộ sưu tập (UC18) — một trang cho hai route (sitemap): `/collections/:id` (chủ bộ: sửa tên / mô tả / công khai, link
 * chia sẻ, ghi chú, bỏ địa điểm, xoá bộ) và `/c/:shareSlug` (link chia sẻ — luôn là góc nhìn công khai, chỉ đọc, kể cả
 * khi chủ tự mở để xem trước như người nhận).
 *
 * Bộ riêng tư / không tồn tại / link đã thu hồi → trang không tìm thấy (backend trả 404 như nhau — không tiết lộ tồn
 * tại). Địa điểm bị ẩn sau khi thêm chỉ chủ thấy, kèm nhãn để tự bỏ ra.
 */
const props = defineProps<{ id?: number; shareSlug?: string }>()

const NOTE_MAX = 500

const uid = useId()
const auth = useAuthStore()
const router = useRouter()
const notify = useNotify()
const queryClient = useQueryClient()
const form = useTemplateRef<InstanceType<typeof CollectionFormDialog>>('form')

const key = computed(() =>
  props.shareSlug ? collectionKeys.shared(props.shareSlug) : collectionKeys.detail(props.id!),
)
const detail = useQuery({
  queryKey: key,
  queryFn: () =>
    props.shareSlug ? collectionsApi.shared(props.shareSlug) : collectionsApi.get(props.id!),
})
const collection = computed(() => detail.data.value)
const notFound = computed(
  () =>
    detail.isError.value &&
    isAxiosError(detail.error.value) &&
    detail.error.value.response?.status === 404,
)
/** Link chia sẻ luôn chỉ đọc; trang `/collections/:id` mới có công cụ của chủ. */
const isOwner = computed(
  () => !props.shareSlug && !!collection.value && collection.value.owner.id === auth.user?.id,
)

watch(
  collection,
  (c) => {
    if (c) document.title = `${c.name} · LocalSpot`
  },
  { immediate: true },
)

const shareUrl = computed(() => {
  const slug = collection.value?.shareSlug
  if (!slug) return null
  return new URL(
    router.resolve({ name: 'shared-collection', params: { shareSlug: slug } }).href,
    window.location.origin,
  ).href
})

async function copyShareUrl(): Promise<void> {
  if (!shareUrl.value) return
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    notify.success('Đã sao chép link chia sẻ.')
  } catch {
    // Trình duyệt chặn clipboard (http, quyền): link vẫn hiện trong ô để chọn và sao chép tay
    notify.info('Không sao chép tự động được — chọn link trong ô rồi sao chép.')
  }
}

function saved(next: CollectionDetail): void {
  queryClient.setQueryData(key.value, next)
  invalidateCollections(queryClient, auth.user?.id)
  notify.success('Đã lưu bộ sưu tập.')
}

// ─── Xoá bộ ─────────────────────────────────────────────────────────────────
const confirmingDelete = ref(false)
const deleting = ref(false)

async function deleteCollection(): Promise<void> {
  const c = collection.value
  if (!c) return
  deleting.value = true
  try {
    await collectionsApi.remove(c.id)
    invalidateCollections(queryClient, auth.user?.id)
    notify.success(`Đã xoá “${c.name}”.`)
    await router.replace({
      name: 'profile',
      params: { id: c.owner.id },
      query: { tab: 'collections' },
    })
  } catch (cause) {
    notify.error(problemMessage(toProblem(cause)))
  } finally {
    deleting.value = false
    confirmingDelete.value = false
  }
}

// ─── Từng địa điểm: ghi chú, bỏ khỏi bộ ─────────────────────────────────────
const editingNote = ref<number | null>(null)
const noteDraft = ref('')
const busyPlace = ref<number | null>(null)

function editNote(placeId: number, note: string | null): void {
  editingNote.value = placeId
  noteDraft.value = note ?? ''
}

async function saveNote(placeId: number): Promise<void> {
  const c = collection.value
  if (!c) return
  busyPlace.value = placeId
  try {
    // PUT idempotent: địa điểm đã có → chỉ sửa ghi chú; xoá hết chữ = bỏ ghi chú
    await collectionsApi.addPlace(c.id, placeId, noteDraft.value.trim() || null)
    editingNote.value = null
    await detail.refetch()
  } catch (cause) {
    notify.error(problemMessage(toProblem(cause)))
  } finally {
    busyPlace.value = null
  }
}

async function removePlace(place: PlaceSummary): Promise<void> {
  const c = collection.value
  if (!c) return
  busyPlace.value = place.id
  try {
    await collectionsApi.removePlace(c.id, place.id)
    invalidateCollections(queryClient, auth.user?.id)
    notify.success(`Đã bỏ “${place.name}” khỏi bộ sưu tập.`)
  } catch (cause) {
    notify.error(problemMessage(toProblem(cause)))
  } finally {
    busyPlace.value = null
  }
}
</script>

<template>
  <NotFoundPage v-if="notFound" />

  <section v-else-if="detail.isError.value" class="py-16" role="alert">
    <h1 class="ls-h1">Chưa tải được bộ sưu tập</h1>
    <p class="mt-4 text-ink-muted">Kiểm tra kết nối mạng rồi thử lại.</p>
    <LsButton class="mt-4" @click="detail.refetch()">Thử lại</LsButton>
  </section>

  <div v-else-if="!collection" aria-busy="true" aria-label="Đang tải bộ sưu tập" class="py-8">
    <span class="ls-skel mb-3 h-4 w-32"></span>
    <span class="ls-skel mb-4 h-11 w-1/2"></span>
    <span class="ls-skel h-5 w-1/3"></span>
  </div>

  <article v-else class="ls-pub py-8">
    <header class="flex flex-wrap items-start justify-between gap-6 border-b border-line pb-8">
      <div class="min-w-0 max-w-[62ch]">
        <span class="ls-kick">Bộ sưu tập</span>
        <h1 class="ls-h1 mt-2 mb-0 [overflow-wrap:anywhere]">{{ collection.name }}</h1>
        <p class="mt-3 mb-0 flex flex-wrap items-center gap-x-3 gap-y-2 text-ink-muted">
          <span>{{ formatNumber(collection.placeCount) }} địa điểm</span>
          <span aria-hidden="true">·</span>
          <span
            >của
            <RouterLink
              class="ls-link"
              :to="{ name: 'profile', params: { id: collection.owner.id } }"
              >{{ collection.owner.displayName }}</RouterLink
            ></span
          >
          <LsBadge v-if="isOwner" :tone="collection.isPublic ? 'info' : 'neutral'">{{
            collection.isPublic ? '● Công khai' : '● Riêng tư'
          }}</LsBadge>
        </p>
        <p v-if="collection.description" class="mt-4 mb-0 whitespace-pre-line">
          {{ collection.description }}
        </p>
      </div>

      <div v-if="isOwner" class="flex flex-wrap items-center gap-2">
        <LsButton variant="ghost" @click="form?.open(collection)">Sửa</LsButton>
        <LsButton v-if="!confirmingDelete" variant="ghost" @click="confirmingDelete = true"
          >Xoá bộ sưu tập</LsButton
        >
        <span v-else role="alert" class="flex flex-wrap items-center gap-2">
          Xoá bộ này? Địa điểm vẫn còn, chỉ mất danh sách.
          <LsButton size="sm" :loading="deleting" @click="deleteCollection">Xoá</LsButton>
          <LsButton size="sm" variant="ghost" @click="confirmingDelete = false">Giữ lại</LsButton>
        </span>
      </div>
    </header>

    <!-- Link chia sẻ (chủ, bộ công khai) -->
    <div v-if="isOwner && shareUrl" class="ls-field mt-6 max-w-[640px]">
      <label :for="`${uid}-share`">Link chia sẻ</label>
      <div class="flex flex-wrap gap-2">
        <input
          :id="`${uid}-share`"
          class="ls-input min-w-0 flex-1"
          :value="shareUrl"
          readonly
          @focus="($event.target as HTMLInputElement).select()"
        />
        <LsButton variant="ghost" @click="copyShareUrl">Sao chép</LsButton>
      </div>
      <p class="m-0 text-caption text-ink-muted">
        Ai có link đều xem được. Chuyển về riêng tư thì link này ngừng hoạt động.
      </p>
    </div>
    <p v-else-if="isOwner" class="mt-6 mb-0 text-caption text-ink-muted">
      Bộ riêng tư — chỉ bạn thấy. Bật “Công khai” trong Sửa để có link chia sẻ.
    </p>

    <!-- Địa điểm -->
    <p v-if="!collection.places.length" class="mt-8 text-ink-muted">
      <template v-if="isOwner"
        >Chưa có địa điểm nào. Mở một địa điểm rồi bấm “+ Lưu” để thêm vào bộ này.
        <RouterLink class="ls-link" :to="{ name: 'search' }"
          >Khám phá địa điểm</RouterLink
        ></template
      >
      <template v-else>Bộ sưu tập chưa có địa điểm nào.</template>
    </p>
    <ul v-else class="mt-8 mb-0 grid list-none gap-x-6 gap-y-10 p-0 sm:grid-cols-2 lg:grid-cols-4">
      <li v-for="item in collection.places" :key="item.place.id" class="flex flex-col">
        <SpotCard :place="item.place" heading-level="h2" />
        <p v-if="item.place.status !== 'APPROVED'" class="m-0 mt-2" role="status">
          <LsBadge tone="neutral">✕ Đã bị ẩn — người khác không thấy</LsBadge>
        </p>

        <form
          v-if="isOwner && editingNote === item.place.id"
          class="ls-field mt-3"
          novalidate
          @submit.prevent="saveNote(item.place.id)"
        >
          <label :for="`${uid}-note-${item.place.id}`">Ghi chú</label>
          <textarea
            :id="`${uid}-note-${item.place.id}`"
            v-model="noteDraft"
            class="ls-textarea"
            rows="2"
            :maxlength="NOTE_MAX"
          ></textarea>
          <div class="flex gap-2">
            <LsButton size="sm" type="submit" :loading="busyPlace === item.place.id">Lưu</LsButton>
            <LsButton size="sm" variant="ghost" @click="editingNote = null">Huỷ</LsButton>
          </div>
        </form>
        <p v-else-if="item.note" class="m-0 mt-2 text-caption whitespace-pre-line">
          {{ item.note }}
        </p>

        <div v-if="isOwner && editingNote !== item.place.id" class="mt-2 flex gap-4">
          <button type="button" class="ls-act" @click="editNote(item.place.id, item.note)">
            {{ item.note ? 'Sửa ghi chú' : 'Thêm ghi chú' }}
          </button>
          <button
            type="button"
            class="ls-act"
            :disabled="busyPlace === item.place.id"
            @click="removePlace(item.place)"
          >
            Bỏ khỏi bộ
          </button>
        </div>
      </li>
    </ul>

    <CollectionFormDialog v-if="isOwner" ref="form" @saved="saved" />
  </article>
</template>
