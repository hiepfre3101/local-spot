<script setup lang="ts">
import { isAxiosError } from 'axios'
import { computed, ref, useId, useTemplateRef } from 'vue'

import { CHECKIN_MAX_ACCURACY_M, CHECKIN_RADIUS_M, checkInsApi } from '@/api/checkins'
import { problemMessage, toProblem } from '@/api/problem'
import LsButton from '@/components/ui/LsButton.vue'
import type { CheckIn, PlaceDetail } from '@/types/api'
import { formatDateTime } from '@/utils/format'
import { formatDistance } from '@/utils/geo'
import { measurePosition, PositionError } from '@/utils/position'

/**
 * P12 · Check-in có kiểm tra vị trí (UC16, FR-24). Mở → trình duyệt xin quyền (lần đầu) → "Đang kiểm tra vị trí…" (radar,
 * tối đa 10 giây) → một kết quả: thành công · chưa đủ gần (Chỉ đường / Thử lại) · chưa có quyền vị trí (Thử lại / Cách
 * bật). Thêm hai trạng thái bản mẫu chưa có nhưng API trả: GPS chưa đủ chính xác (> 100 m) và hôm nay đã check-in.
 * `aria-live` để trình đọc màn hình đọc kết quả; radar đứng yên khi "giảm chuyển động" (CSS).
 *
 * Vị trí chỉ gửi một lần để xác nhận (backend không trả lại toạ độ); ngưỡng hiển thị lấy từ luật backend (200 m, ±100 m)
 * — kiểm thật ở máy chủ. Thành công phát `checkedIn` để trang cập nhật nút và số lượt.
 */
const props = defineProps<{ place: PlaceDetail }>()
/** `refresh`: máy chủ báo hôm nay đã check-in (trang đang hiện nút cũ) — trang nạp lại chi tiết. */
const emit = defineEmits<{ checkedIn: [checkIn: CheckIn]; refresh: []; directions: [] }>()

type State =
  | { kind: 'checking'; accuracyM: number | null }
  | { kind: 'ok'; checkIn: CheckIn }
  | { kind: 'far'; distanceM: number | null }
  | { kind: 'inaccurate'; accuracyM: number }
  | { kind: 'denied' }
  | { kind: 'unavailable' }
  | { kind: 'already' }
  | { kind: 'error'; message: string }

const id = useId()
const dialog = useTemplateRef<HTMLDialogElement>('dialog')
const state = ref<State>({ kind: 'checking', accuracyM: null })
const showHelp = ref(false)
/** Huỷ giữa chừng: bỏ qua kết quả về muộn của lần đo / gửi đang chạy. */
let attempt = 0

const title = computed(() => {
  switch (state.value.kind) {
    case 'checking':
      return 'Đang kiểm tra vị trí của bạn…'
    case 'ok':
      return 'Check-in thành công'
    case 'far':
      return 'Bạn chưa ở đủ gần'
    case 'inaccurate':
      return 'Vị trí chưa đủ chính xác'
    case 'denied':
      return 'Chưa có quyền vị trí'
    case 'unavailable':
      return 'Chưa lấy được vị trí'
    case 'already':
      return 'Hôm nay bạn đã check-in'
    case 'error':
      return 'Chưa check-in được'
  }
  return ''
})

function open(): void {
  showHelp.value = false
  dialog.value?.showModal()
  void run()
}

defineExpose({ open })

function close(): void {
  attempt += 1
  dialog.value?.close()
}

async function run(): Promise<void> {
  const current = ++attempt
  state.value = { kind: 'checking', accuracyM: null }
  showHelp.value = false
  let position
  try {
    position = await measurePosition()
  } catch (cause) {
    if (current !== attempt) return
    const reason = cause instanceof PositionError ? cause.reason : 'unavailable'
    state.value = reason === 'denied' ? { kind: 'denied' } : { kind: 'unavailable' }
    return
  }
  if (current !== attempt) return
  state.value = { kind: 'checking', accuracyM: Math.round(position.accuracyM) }
  // Như backend: GPS kém thì không gửi — đỡ một request chắc chắn bị từ chối
  if (position.accuracyM > CHECKIN_MAX_ACCURACY_M) {
    state.value = { kind: 'inaccurate', accuracyM: Math.round(position.accuracyM) }
    return
  }
  try {
    const checkIn = await checkInsApi.checkIn(props.place.id, position)
    if (current !== attempt) return
    state.value = { kind: 'ok', checkIn }
    emit('checkedIn', checkIn)
  } catch (cause) {
    if (current !== attempt) return
    const problem = toProblem(cause)
    switch (problem.code) {
      case 'CHECKIN_TOO_FAR': {
        const data = isAxiosError(cause)
          ? (cause.response?.data as { distanceM?: number })
          : undefined
        state.value = { kind: 'far', distanceM: data?.distanceM ?? null }
        break
      }
      case 'GPS_INACCURATE':
        state.value = { kind: 'inaccurate', accuracyM: Math.round(position.accuracyM) }
        break
      case 'CHECKIN_ALREADY_TODAY':
        state.value = { kind: 'already' }
        emit('refresh')
        break
      case 'EMAIL_NOT_VERIFIED':
        state.value = { kind: 'error', message: 'Xác thực email trước khi check-in.' }
        break
      case 'SELF_ACTION_FORBIDDEN':
        state.value = { kind: 'error', message: 'Chủ địa điểm không check-in địa điểm của mình.' }
        break
      default:
        state.value = { kind: 'error', message: problemMessage(problem) }
    }
  }
}

/** Khoảng cách chính xác tới mét khi gần (42 m), làm tròn khi xa (1,2 km). */
function distanceText(meters: number): string {
  return meters < 1000 ? `${meters} m` : formatDistance(meters)
}

function directions(): void {
  close()
  emit('directions')
}
</script>

<template>
  <dialog
    ref="dialog"
    class="ls-dialog m-auto"
    :aria-labelledby="`${id}-title`"
    @cancel="attempt += 1"
  >
    <button type="button" class="ls-iconbtn ls-dialog__x" aria-label="Đóng" @click="close">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M6 6l12 12M18 6L6 18" />
      </svg>
    </button>
    <div class="ls-dialog__body" aria-live="polite">
      <!-- Biểu tượng -->
      <div v-if="state.kind === 'checking'" class="ls-radar" aria-hidden="true">
        <i></i><i></i><i></i><b></b>
      </div>
      <span
        v-else-if="state.kind === 'ok' || state.kind === 'already'"
        class="ls-state ls-state--ok"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12l5 5 9-10" /></svg>
      </span>
      <span
        v-else-if="state.kind === 'far' || state.kind === 'inaccurate'"
        class="ls-state ls-state--far"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 21s-7-6.2-7-11a7 7 0 0114 0c0 4.8-7 11-7 11z" />
          <circle cx="12" cy="10" r="2.5" />
        </svg>
      </span>
      <span v-else class="ls-state ls-state--off">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 21s-7-6.2-7-11a7 7 0 0111.5-5.4M18.6 7.5A7 7 0 0119 10c0 4.8-7 11-7 11" />
          <path d="M3 3l18 18" />
        </svg>
      </span>

      <h2 :id="`${id}-title`">{{ title }}</h2>

      <!-- Nội dung theo trạng thái -->
      <template v-if="state.kind === 'checking'">
        <p>
          Giữ trang mở trong vài giây. LocalSpot chỉ dùng vị trí lúc này để xác nhận bạn đang ở
          <b class="text-ink">{{ place.name }}</b
          >, không lưu lịch sử di chuyển.
        </p>
        <div class="ls-dialog__meta">
          <span>Bán kính cho phép: {{ CHECKIN_RADIUS_M }} m</span>
          <span v-if="state.accuracyM !== null">Độ chính xác: ±{{ state.accuracyM }} m</span>
        </div>
        <div class="ls-dialog__acts">
          <LsButton variant="ghost" size="sm" @click="close">Huỷ</LsButton>
        </div>
      </template>

      <template v-else-if="state.kind === 'ok'">
        <p>
          Bạn đang cách {{ place.name }} {{ distanceText(state.checkIn.distanceM) }}. Lượt check-in
          đã được ghi lúc {{ formatDateTime(state.checkIn.createdAt) }}.
        </p>
        <div class="ls-dialog__meta">+1 điểm đóng góp</div>
        <div class="ls-dialog__acts">
          <LsButton
            v-if="place.myReviewId === null && $router.hasRoute('write-review')"
            size="sm"
            :to="{ name: 'write-review', params: { slug: place.slug } }"
            >Viết đánh giá</LsButton
          >
          <LsButton variant="ghost" size="sm" @click="close">Đóng</LsButton>
        </div>
      </template>

      <template v-else-if="state.kind === 'far'">
        <p>
          <template v-if="state.distanceM !== null"
            >Bạn đang cách khoảng {{ distanceText(state.distanceM) }}.
          </template>
          Cần ở trong bán kính {{ CHECKIN_RADIUS_M }} m quanh địa điểm để check-in.
        </p>
        <div class="ls-dialog__acts">
          <LsButton size="sm" @click="directions">Chỉ đường</LsButton>
          <LsButton variant="ghost" size="sm" @click="run">Thử lại</LsButton>
        </div>
      </template>

      <template v-else-if="state.kind === 'inaccurate'">
        <p>
          Độ chính xác hiện tại ±{{ state.accuracyM }} m — cần trong khoảng ±{{
            CHECKIN_MAX_ACCURACY_M
          }}
          m. Bật GPS / định vị chính xác, ra chỗ thoáng rồi thử lại.
        </p>
        <div class="ls-dialog__acts">
          <LsButton size="sm" @click="run">Thử lại</LsButton>
          <LsButton variant="ghost" size="sm" @click="close">Đóng</LsButton>
        </div>
      </template>

      <template v-else-if="state.kind === 'denied'">
        <p>
          Trình duyệt đang chặn vị trí cho LocalSpot. Bật quyền vị trí trong cài đặt trình duyệt rồi
          thử lại.
        </p>
        <p v-if="showHelp" class="text-left">
          Bấm biểu tượng ổ khoá cạnh địa chỉ trang → <b class="text-ink">Vị trí</b> →
          <b class="text-ink">Cho phép</b>. Trên điện thoại, bật thêm Dịch vụ vị trí cho trình duyệt
          trong Cài đặt của máy.
        </p>
        <div class="ls-dialog__acts">
          <LsButton size="sm" @click="run">Thử lại</LsButton>
          <LsButton
            v-if="!showHelp"
            variant="ghost"
            size="sm"
            :aria-expanded="showHelp"
            @click="showHelp = true"
            >Cách bật</LsButton
          >
        </div>
      </template>

      <template v-else-if="state.kind === 'unavailable'">
        <p>
          Thiết bị chưa trả về vị trí (hết 10 giây hoặc không hỗ trợ định vị). Bật GPS rồi thử lại.
        </p>
        <div class="ls-dialog__acts">
          <LsButton size="sm" @click="run">Thử lại</LsButton>
          <LsButton variant="ghost" size="sm" @click="close">Đóng</LsButton>
        </div>
      </template>

      <template v-else-if="state.kind === 'already'">
        <p>Mỗi địa điểm check-in được một lần mỗi ngày. Hẹn bạn lần ghé sau!</p>
        <div class="ls-dialog__acts">
          <LsButton variant="ghost" size="sm" @click="close">Đóng</LsButton>
        </div>
      </template>

      <template v-else>
        <p role="alert">✕ {{ state.message }}</p>
        <div class="ls-dialog__acts">
          <LsButton size="sm" @click="run">Thử lại</LsButton>
          <LsButton variant="ghost" size="sm" @click="close">Đóng</LsButton>
        </div>
      </template>
    </div>
  </dialog>
</template>
