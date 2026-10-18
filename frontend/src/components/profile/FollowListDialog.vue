<script setup lang="ts">
import { useInfiniteQuery } from '@tanstack/vue-query'
import { computed, ref, useId, useTemplateRef } from 'vue'
import { RouterLink } from 'vue-router'

import { usersApi } from '@/api/users'
import LsAvatar from '@/components/ui/LsAvatar.vue'
import LsButton from '@/components/ui/LsButton.vue'

/**
 * Danh sách người theo dõi / đang theo dõi (UC20 — công khai, sitemap 2026-10-12), theo dõi gần nhất trước, "Xem thêm"
 * theo cursor. Mở bằng `open('followers' | 'following')`; bấm một người → sang trang cá nhân của họ và đóng hộp thoại.
 */
const props = defineProps<{ userId: number }>()

type Kind = 'followers' | 'following'
const TITLES: Record<Kind, string> = { followers: 'Người theo dõi', following: 'Đang theo dõi' }

const id = useId()
const dialog = useTemplateRef<HTMLDialogElement>('dialog')
const kind = ref<Kind | null>(null)

const list = useInfiniteQuery({
  queryKey: computed(() => ['user', props.userId, kind.value]),
  queryFn: ({ pageParam }) =>
    kind.value === 'following'
      ? usersApi.following(props.userId, pageParam)
      : usersApi.followers(props.userId, pageParam),
  initialPageParam: null as string | null,
  getNextPageParam: (last) => last.nextCursor,
  enabled: computed(() => kind.value !== null),
})
const people = computed(() => list.data.value?.pages.flatMap((p) => p.items) ?? [])

function open(next: Kind): void {
  kind.value = next
  dialog.value?.showModal()
}

function close(): void {
  dialog.value?.close()
}

defineExpose({ open })
</script>

<template>
  <dialog
    ref="dialog"
    class="ls-pop m-auto w-[min(440px,calc(100vw-32px))] p-0 text-ink backdrop:bg-ink/60"
    :aria-labelledby="`${id}-title`"
    @close="kind = null"
  >
    <div class="flex items-center justify-between gap-4 border-b border-line p-4">
      <h2 :id="`${id}-title`" class="m-0 text-title">{{ kind ? TITLES[kind] : '' }}</h2>
      <LsButton variant="ghost" size="sm" @click="close">Đóng</LsButton>
    </div>
    <div class="max-h-[60vh] overflow-y-auto p-4" :aria-busy="list.isFetching.value">
      <p v-if="list.isError.value" role="alert" class="m-0">✕ Chưa tải được danh sách.</p>
      <p v-else-if="!list.isPending.value && people.length === 0" class="m-0 text-ink-muted">
        Chưa có ai.
      </p>
      <ul class="m-0 flex list-none flex-col gap-3 p-0">
        <li v-for="person in people" :key="person.id">
          <RouterLink
            class="flex items-center gap-3 text-ink"
            :to="{ name: 'profile', params: { id: person.id } }"
            @click="close"
          >
            <LsAvatar size="sm" :name="person.displayName" :src="person.avatarUrl" />
            <b>{{ person.displayName }}</b>
          </RouterLink>
        </li>
      </ul>
      <div v-if="list.hasNextPage.value" class="pt-4">
        <LsButton
          variant="ghost"
          size="sm"
          :loading="list.isFetchingNextPage.value"
          @click="list.fetchNextPage()"
          >Xem thêm</LsButton
        >
      </div>
    </div>
  </dialog>
</template>
