<script setup lang="ts">
import { useInfiniteQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, useId } from 'vue'
import { useRoute } from 'vue-router'

import { interactionsApi } from '@/api/interactions'
import { problemMessage, toProblem } from '@/api/problem'
import LsAvatar from '@/components/ui/LsAvatar.vue'
import LsButton from '@/components/ui/LsButton.vue'
import { useAuthStore } from '@/stores/auth'
import type { Comment } from '@/types/api'
import { formatDate, retryAfterText } from '@/utils/format'

import type { ReportTarget } from './reportTarget'

/**
 * Bình luận của một đánh giá (UC15, FR-21) — 2 cấp: bình luận gốc cũ trước, mỗi gốc kèm toàn bộ trả lời; chỉ trả lời
 * được bình luận gốc. Gốc đã xoá / bị ẩn nhưng còn trả lời → dòng giữ chỗ "Bình luận đã bị xoá". Người viết xoá được
 * bình luận của mình (xác nhận tại chỗ); bình luận của người khác thì báo cáo (UC17). Khách thấy hết, viết thì đăng
 * nhập. Vượt 20 bình luận / 10 phút → báo thời gian chờ (429).
 */
const props = defineProps<{ reviewId: number }>()
const emit = defineEmits<{ report: [target: ReportTarget]; countChange: [delta: number] }>()

const CONTENT_MAX = 2000
const id = useId()
const auth = useAuthStore()
const route = useRoute()
const queryClient = useQueryClient()
const queryKey = computed(() => ['review', props.reviewId, 'comments'])

const comments = useInfiniteQuery({
  queryKey,
  queryFn: ({ pageParam }) => interactionsApi.comments(props.reviewId, pageParam),
  initialPageParam: null as string | null,
  getNextPageParam: (last) => last.nextCursor,
})
const roots = computed(() => comments.data.value?.pages.flatMap((p) => p.items) ?? [])

const draft = ref('')
const replyTo = ref<number | null>(null)
const replyDraft = ref('')
const sending = ref(false)
const error = ref<string | null>(null)
const confirmingDelete = ref<number | null>(null)

const loginTarget = computed(() => ({ name: 'login', query: { redirect: route.fullPath } }))
const unverified = computed(() => auth.user !== null && !auth.user.emailVerified)

function isMine(comment: Comment): boolean {
  return comment.author !== null && comment.author.id === auth.user?.id
}

async function send(content: string, parentId: number | null): Promise<boolean> {
  const text = content.trim()
  if (!text) {
    error.value = 'Viết nội dung bình luận.'
    return false
  }
  sending.value = true
  error.value = null
  try {
    await interactionsApi.addComment(props.reviewId, text, parentId)
    emit('countChange', 1)
    await queryClient.invalidateQueries({ queryKey: queryKey.value })
    return true
  } catch (cause) {
    const problem = toProblem(cause)
    error.value =
      problem.status === 429 && problem.retryAfter
        ? `Bạn bình luận hơi nhanh — thử lại sau ${retryAfterText(problem.retryAfter)}.`
        : (Object.values(problem.fieldErrors)[0] ?? problemMessage(problem))
    return false
  } finally {
    sending.value = false
  }
}

async function sendRoot(): Promise<void> {
  if (await send(draft.value, null)) draft.value = ''
}

async function sendReply(rootId: number): Promise<void> {
  if (await send(replyDraft.value, rootId)) {
    replyDraft.value = ''
    replyTo.value = null
  }
}

function startReply(rootId: number): void {
  replyTo.value = replyTo.value === rootId ? null : rootId
  replyDraft.value = ''
  error.value = null
}

async function remove(comment: Comment): Promise<void> {
  error.value = null
  try {
    await interactionsApi.deleteComment(comment.id)
    emit('countChange', -1)
    confirmingDelete.value = null
    await queryClient.invalidateQueries({ queryKey: queryKey.value })
  } catch (cause) {
    error.value = problemMessage(toProblem(cause))
  }
}

function report(comment: Comment): void {
  emit('report', {
    type: 'COMMENT',
    id: comment.id,
    label: `bình luận của ${comment.author?.displayName ?? 'người dùng'}`,
  })
}
</script>

<template>
  <section class="mt-3 flex flex-col gap-4" :aria-labelledby="`${id}-title`">
    <h3 :id="`${id}-title`" class="sr-only">Bình luận</h3>

    <p v-if="comments.isError.value" role="alert" class="m-0">✕ Chưa tải được bình luận.</p>
    <p
      v-else-if="!comments.isPending.value && roots.length === 0"
      class="m-0 text-caption text-ink-muted"
    >
      Chưa có bình luận.
    </p>

    <ul class="m-0 flex list-none flex-col gap-4 p-0">
      <li v-for="root in roots" :key="root.id">
        <template v-for="comment in [root, ...(root.replies ?? [])]" :key="comment.id">
          <div :class="['flex gap-3', comment.parentId !== null && 'mt-3 ml-11']">
            <template v-if="comment.deleted">
              <p class="m-0 text-caption text-ink-muted italic">Bình luận đã bị xoá.</p>
            </template>
            <template v-else>
              <LsAvatar
                size="sm"
                :name="comment.author?.displayName ?? '?'"
                :src="comment.author?.avatarUrl"
              />
              <div class="min-w-0">
                <p class="m-0 text-caption">
                  <b>{{ comment.author?.displayName }}</b>
                  <span class="text-ink-muted">
                    ·
                    <time :datetime="comment.createdAt">{{
                      formatDate(comment.createdAt)
                    }}</time></span
                  >
                </p>
                <p class="m-0 break-words whitespace-pre-line">{{ comment.content }}</p>
                <div class="ls-actions">
                  <button
                    v-if="comment.parentId === null && auth.isAuthenticated"
                    type="button"
                    class="ls-act"
                    :aria-expanded="replyTo === comment.id"
                    @click="startReply(comment.id)"
                  >
                    Trả lời
                  </button>
                  <template v-if="isMine(comment)">
                    <button
                      v-if="confirmingDelete !== comment.id"
                      type="button"
                      class="ls-act"
                      @click="confirmingDelete = comment.id"
                    >
                      Xoá
                    </button>
                    <span v-else role="alert" class="flex items-center gap-1 text-caption">
                      Xoá bình luận này?
                      <button type="button" class="ls-act" @click="remove(comment)">Xoá</button>
                      <button type="button" class="ls-act" @click="confirmingDelete = null">
                        Giữ lại
                      </button>
                    </span>
                  </template>
                  <button
                    v-else-if="auth.isAuthenticated"
                    type="button"
                    class="ls-act"
                    @click="report(comment)"
                  >
                    Báo cáo
                  </button>
                </div>
              </div>
            </template>
          </div>
        </template>

        <form
          v-if="replyTo === root.id"
          class="mt-3 ml-11 flex flex-col gap-2"
          @submit.prevent="sendReply(root.id)"
        >
          <label class="sr-only" :for="`${id}-reply-${root.id}`">Trả lời bình luận</label>
          <textarea
            :id="`${id}-reply-${root.id}`"
            v-model="replyDraft"
            class="ls-textarea"
            rows="2"
            :maxlength="CONTENT_MAX"
            placeholder="Viết câu trả lời…"
          ></textarea>
          <div class="flex gap-2">
            <LsButton type="submit" size="sm" :loading="sending">Gửi trả lời</LsButton>
            <LsButton variant="ghost" size="sm" @click="replyTo = null">Huỷ</LsButton>
          </div>
        </form>
      </li>
    </ul>

    <div v-if="comments.hasNextPage.value">
      <LsButton
        variant="ghost"
        size="sm"
        :loading="comments.isFetchingNextPage.value"
        @click="comments.fetchNextPage()"
        >Xem thêm bình luận</LsButton
      >
    </div>

    <p v-if="error" role="alert" class="ls-field__error m-0">✕ {{ error }}</p>

    <form
      v-if="auth.isAuthenticated && !unverified"
      class="flex flex-col gap-2"
      @submit.prevent="sendRoot"
    >
      <label class="sr-only" :for="`${id}-new`">Viết bình luận</label>
      <textarea
        :id="`${id}-new`"
        v-model="draft"
        class="ls-textarea"
        rows="2"
        :maxlength="CONTENT_MAX"
        placeholder="Viết bình luận…"
      ></textarea>
      <div>
        <LsButton type="submit" size="sm" :loading="sending">Gửi bình luận</LsButton>
      </div>
    </form>
    <p v-else-if="unverified" class="m-0 text-caption text-ink-muted">
      Xác thực email để bình luận — liên kết ở băng nhắc phía trên.
    </p>
    <p v-else class="m-0 text-caption">
      <RouterLink class="ls-link" :to="loginTarget">Đăng nhập</RouterLink> để bình luận.
    </p>
  </section>
</template>
