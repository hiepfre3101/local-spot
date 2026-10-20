<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, useTemplateRef } from 'vue'
import { RouterLink } from 'vue-router'

import { useNotify } from '@/plugins/toast'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore, type NotificationFilter } from '@/stores/notification'
import type { AppNotification } from '@/types/api'
import { relativeTime } from '@/utils/format'

import { describeNotification, NOTIFICATION_ICONS } from './notificationView'

/**
 * Chuông + bảng thông báo thả xuống (P07 — UC19, FR-27). Bảng 400px, viền `line-strong` thay bóng; lọc Tất cả / Chưa
 * đọc; dòng chưa đọc có nền `surface` + chấm `sea` + chữ "· Mới" (không chỉ bằng màu). Số trên chuông tối đa "9+".
 * Không có trang riêng (O4, chốt 2026-10-19) — "Xem cũ hơn" tải tiếp ngay trong bảng.
 *
 * `admin`: đặt ở chân sidebar quản trị (kiểm duyệt viên nhận yêu cầu sở hữu mới — chốt 2026-10-19), bảng mở sang phải
 * sidebar, bo góc tối thiểu theo quy tắc trang quản trị.
 */
const props = withDefaults(defineProps<{ variant?: 'public' | 'admin' }>(), { variant: 'public' })

const auth = useAuthStore()
const store = useNotificationStore()
const notify = useNotify()
const panelId = useId()
const open = ref(false)
const root = useTemplateRef<HTMLElement>('root')
const bell = useTemplateRef<HTMLButtonElement>('bell')

const badge = computed(() => (store.unreadCount > 9 ? '9+' : String(store.unreadCount)))
const bellLabel = computed(() =>
  store.unreadCount > 0 ? `Thông báo, ${store.unreadCount} mới` : 'Thông báo',
)
const rows = computed(() =>
  store.items.map((n) => ({ n, view: describeNotification(n, auth.user?.id ?? 0) })),
)

function toggle(): void {
  open.value = !open.value
  if (open.value) {
    void store.ensureLoaded()
  }
}

function close(returnFocus = false): void {
  open.value = false
  if (returnFocus) {
    bell.value?.focus()
  }
}

function setFilter(value: NotificationFilter): void {
  void store.setFilter(value)
}

async function markAllRead(): Promise<void> {
  try {
    await store.markAllRead()
  } catch {
    notify.error('Chưa đánh dấu được. Thử lại sau.')
  }
}

/** Bấm một thông báo: đánh dấu đã đọc; có đích thì RouterLink tự chuyển trang, bảng đóng lại. */
function activate(n: AppNotification, hasTarget: boolean): void {
  void store.markRead(n)
  if (hasTarget) {
    close()
  }
}

function onDocumentClick(event: MouseEvent): void {
  if (open.value && root.value && !root.value.contains(event.target as Node)) {
    close()
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (open.value && event.key === 'Escape') {
    close(true)
  }
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div ref="root" :class="props.variant === 'public' && 'relative'">
    <button
      ref="bell"
      type="button"
      class="ls-iconbtn"
      :aria-label="bellLabel"
      :aria-expanded="open"
      :aria-controls="panelId"
      @click="toggle"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M6 16V11a6 6 0 0112 0v5l2 2H4z" />
        <path d="M10 20a2 2 0 004 0" />
      </svg>
      <span v-if="store.unreadCount > 0" class="dot" aria-hidden="true">{{ badge }}</span>
    </button>

    <section
      v-show="open"
      :id="panelId"
      :class="[
        'ls-pop ls-notes z-[1150]',
        props.variant === 'admin'
          ? 'ls-pop--admin fixed inset-x-4 top-4 md:inset-x-auto md:bottom-4 md:left-[232px] md:top-auto'
          : 'max-md:fixed max-md:inset-x-4 max-md:top-16 md:absolute md:right-0 md:top-12',
      ]"
      aria-label="Thông báo"
    >
      <div class="ls-pop__head">
        <b>Thông báo</b>
        <button
          type="button"
          class="ls-link cursor-pointer border-0 bg-transparent p-0 disabled:cursor-default disabled:text-ink-muted disabled:no-underline"
          :disabled="store.unreadCount === 0"
          @click="markAllRead"
        >
          Đánh dấu đã đọc tất cả
        </button>
      </div>
      <div class="ls-notes__filter">
        <button
          type="button"
          class="ls-fchip"
          :aria-pressed="store.filter === 'all'"
          @click="setFilter('all')"
        >
          Tất cả
        </button>
        <button
          type="button"
          class="ls-fchip"
          :aria-pressed="store.filter === 'unread'"
          @click="setFilter('unread')"
        >
          Chưa đọc<template v-if="store.unreadCount > 0"> · {{ store.unreadCount }}</template>
        </button>
      </div>

      <ul class="ls-notes__list" aria-live="polite">
        <li
          v-for="{ n, view } in rows"
          :key="n.id"
          :class="['ls-note', !n.readAt && 'ls-note--new']"
        >
          <span class="ls-noteicon">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path :d="NOTIFICATION_ICONS[view.icon]" />
            </svg>
          </span>
          <div>
            <component
              :is="view.to ? RouterLink : n.readAt ? 'span' : 'button'"
              v-bind="view.to ? { to: view.to } : n.readAt ? {} : { type: 'button' }"
              class="ls-note__body"
              :title="!view.to && !n.readAt ? 'Đánh dấu đã đọc' : undefined"
              @click="activate(n, view.to !== null)"
            >
              <template v-for="(part, i) in view.parts" :key="i">
                <b v-if="part.strong">{{ part.text }}</b>
                <template v-else>{{ part.text }}</template>
              </template>
            </component>
            <span v-if="view.detail" class="ls-note__detail">{{ view.detail }}</span>
            <time :datetime="n.createdAt">
              {{ relativeTime(n.createdAt) }}<template v-if="!n.readAt"> · Mới</template>
            </time>
          </div>
          <span v-if="!n.readAt" class="unread" aria-hidden="true"></span>
          <span v-else></span>
        </li>
      </ul>

      <p v-if="store.loading" class="ls-notes__state">Đang tải…</p>
      <p v-else-if="store.loadError" class="ls-notes__state">
        ✕ Không tải được thông báo.
        <button
          type="button"
          class="ls-link cursor-pointer border-0 bg-transparent p-0"
          @click="store.loaded ? store.loadMore() : store.ensureLoaded()"
        >
          Thử lại
        </button>
      </p>
      <p v-else-if="store.loaded && rows.length === 0" class="ls-notes__state">
        {{
          store.filter === 'unread'
            ? 'Bạn đã đọc hết thông báo.'
            : 'Chưa có thông báo nào. Khi đề xuất được duyệt hay chủ quán phản hồi, bạn sẽ thấy ở đây.'
        }}
      </p>
      <div v-else-if="store.hasMore" class="ls-notes__state">
        <button
          type="button"
          class="ls-link cursor-pointer border-0 bg-transparent p-0"
          @click="store.loadMore()"
        >
          Xem cũ hơn
        </button>
      </div>
    </section>
  </div>
</template>
