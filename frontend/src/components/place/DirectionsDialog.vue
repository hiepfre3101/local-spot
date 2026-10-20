<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { storeToRefs } from 'pinia'
import { computed, defineAsyncComponent, ref, useTemplateRef } from 'vue'

import LsButton from '@/components/ui/LsButton.vue'
import { usePlaceStore } from '@/stores/place'
import type { PlaceDetail } from '@/types/api'
import { distanceM, formatDistance } from '@/utils/geo'
import { RouteNotFoundError, fetchRoute, formatDuration, type TravelMode } from '@/utils/routing'

/**
 * Hộp thoại chỉ đường của P03 (chốt 2026-10-13 — không chuyển sang trang ngoài): bản đồ lớn, ghim địa điểm, vị trí người
 * dùng và tuyến đường (OSRM — `utils/routing.ts`) kèm quãng đường + thời gian ước tính. Vị trí chỉ hỏi khi mở hộp thoại,
 * dùng chung store `place` với "Quanh tôi". Không lấy được vị trí → vẫn có bản đồ + địa chỉ; dịch vụ chỉ đường lỗi →
 * khoảng cách đường chim bay, không để người dùng trắng tay.
 */
const props = defineProps<{ place: PlaceDetail }>()

const PlaceMap = defineAsyncComponent(() => import('./PlaceMap.vue'))

const dialog = useTemplateRef<HTMLDialogElement>('dialog')
const isOpen = ref(false)
const mode = ref<TravelMode>('drive')
const placeStore = usePlaceStore()
const { location, locateStatus } = storeToRefs(placeStore)

function open(): void {
  dialog.value?.showModal()
  isOpen.value = true
  void placeStore.locate()
}

function close(): void {
  dialog.value?.close()
}

defineExpose({ open })

const destination = computed(() => props.place.location)
const route = useQuery({
  queryKey: computed(() => ['route', mode.value, location.value, destination.value]),
  queryFn: ({ signal }) => fetchRoute(location.value!, destination.value, mode.value, signal),
  enabled: computed(() => isOpen.value && location.value !== null),
  staleTime: 5 * 60_000,
  retry: (count, error) => count < 1 && !(error instanceof RouteNotFoundError),
})

const straightLine = computed(() =>
  location.value ? distanceM(location.value, destination.value) : null,
)
const MODES: { value: TravelMode; label: string }[] = [
  { value: 'drive', label: 'Xe máy / ô tô' },
  { value: 'walk', label: 'Đi bộ' },
]
</script>

<template>
  <dialog
    ref="dialog"
    class="ls-pop m-auto h-[min(760px,calc(100vh-32px))] w-[min(1100px,calc(100vw-32px))] p-0 text-ink backdrop:bg-ink/60"
    :aria-label="`Chỉ đường tới ${place.name}`"
    @close="isOpen = false"
    @click.self="close"
  >
    <div class="flex h-full flex-col">
      <div class="flex items-center justify-between gap-4 border-b border-line p-4">
        <h2 class="m-0 text-title">Chỉ đường tới {{ place.name }}</h2>
        <LsButton variant="ghost" size="sm" @click="close">Đóng</LsButton>
      </div>

      <div class="grid min-h-0 flex-1 md:grid-cols-[1fr_320px]">
        <PlaceMap
          v-if="isOpen"
          class="min-h-[280px]"
          :places="[place]"
          pin-label="name"
          :user-location="location"
          :route="route.data.value?.path ?? null"
        />

        <div
          class="flex flex-col gap-5 overflow-y-auto border-line p-5 max-md:border-t md:border-l"
        >
          <div class="flex flex-wrap gap-2" role="group" aria-label="Phương tiện">
            <button
              v-for="m in MODES"
              :key="m.value"
              class="ls-fchip"
              type="button"
              :aria-pressed="mode === m.value"
              @click="mode = m.value"
            >
              {{ m.label }}
            </button>
          </div>

          <div aria-live="polite">
            <template v-if="locateStatus === 'locating' || (locateStatus === 'idle' && !location)">
              <p class="m-0 font-semibold">Đang lấy vị trí của bạn…</p>
              <p class="mt-1 mb-0 text-caption text-ink-muted">
                Trình duyệt có thể hỏi quyền truy cập vị trí — chọn Cho phép.
              </p>
            </template>

            <template v-else-if="!location">
              <p class="m-0 font-semibold">✕ Không lấy được vị trí của bạn</p>
              <p class="mt-1 mb-0 text-caption text-ink-muted">
                {{
                  locateStatus === 'denied'
                    ? 'Bạn đã chặn quyền vị trí cho trang này — bật lại trong cài đặt trình duyệt để xem đường đi.'
                    : 'Thiết bị không xác định được vị trí lúc này.'
                }}
              </p>
              <LsButton class="mt-3" size="sm" @click="placeStore.locate()">Thử lại</LsButton>
            </template>

            <template v-else-if="route.isPending.value">
              <p class="m-0 font-semibold">Đang tìm đường…</p>
            </template>

            <template v-else-if="route.data.value">
              <p class="route-metric m-0">{{ formatDuration(route.data.value.durationS) }}</p>
              <p class="mt-1 mb-0 text-ink-muted">
                {{ formatDistance(route.data.value.distanceM) }} ·
                {{ mode === 'walk' ? 'đi bộ' : 'theo đường xe' }}
              </p>
            </template>

            <template v-else>
              <p class="m-0 font-semibold">
                ✕
                {{
                  route.error.value instanceof RouteNotFoundError
                    ? 'Không tìm được đường đi tới đây'
                    : 'Chưa tải được đường đi'
                }}
              </p>
              <p v-if="straightLine !== null" class="mt-1 mb-0 text-caption text-ink-muted">
                Cách bạn khoảng {{ formatDistance(straightLine) }} theo đường chim bay.
              </p>
              <LsButton
                v-if="!(route.error.value instanceof RouteNotFoundError)"
                class="mt-3"
                size="sm"
                @click="route.refetch()"
              >
                Thử lại
              </LsButton>
            </template>
          </div>

          <ol class="route-legs m-0 list-none p-0">
            <li>
              <span class="dot dot--me" aria-hidden="true"></span>
              <span><b>Vị trí của bạn</b></span>
            </li>
            <li>
              <span class="dot dot--place" aria-hidden="true"></span>
              <span>
                <b>{{ place.name }}</b>
                <br />
                <span class="text-ink-muted">{{ place.address }}</span>
              </span>
            </li>
          </ol>

          <p class="mt-auto mb-0 text-caption text-ink-muted">
            Thời gian là ước tính; xe máy dùng tuyến đường xe. Chỉ đường: OSRM · dữ liệu ©
            OpenStreetMap.
          </p>
        </div>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
/* Cỡ `metric` của design system cho con số chính */
.route-metric {
  font: 700 30px/36px var(--font-sans);
  letter-spacing: -0.01em;
  font-variant-numeric: tabular-nums;
}
.route-legs {
  border-top: 1px solid var(--line);
}
.route-legs li {
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: 12px;
  align-items: start;
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
  font: 400 14px/20px var(--font-sans);
}
.dot {
  width: 14px;
  height: 14px;
  margin-top: 3px;
  border-radius: var(--radius-pill);
}
.dot--me {
  background: var(--sea);
  border: 3px solid var(--surface-raised);
  outline: 1px solid var(--sea);
}
.dot--place {
  background: var(--olive);
}
</style>
