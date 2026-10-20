<script setup lang="ts">
import 'leaflet/dist/leaflet.css'

import * as L from 'leaflet'
import { storeToRefs } from 'pinia'
import { onBeforeUnmount, onMounted, shallowRef, useTemplateRef, watch } from 'vue'

import LsButton from '@/components/ui/LsButton.vue'
import { useTheme } from '@/composables/useTheme'
import { usePlaceStore } from '@/stores/place'
import type { GeoPoint } from '@/types/api'
import { tileSource } from '@/utils/mapTiles'

/**
 * Chọn vị trí địa điểm (UC11 bước 3 — supervisor chốt 2026-10-17: không dùng dịch vụ đổi địa chỉ ra toạ độ): bấm lên
 * bản đồ để đặt ghim, kéo ghim để chỉnh, hoặc "Vị trí của tôi" (dùng chung store `place` với "Quanh tôi"). Ghim dùng
 * kiểu `ls-pin` như bản đồ khám phá. Bàn phím: ghim focus được và kéo được bằng chuột; toạ độ hiện bằng chữ bên dưới để
 * không chỉ dựa vào bản đồ.
 */
const location = defineModel<GeoPoint | null>({ required: true })

/** Trung tâm Hà Nội — khi chưa có ghim / vị trí. */
const FALLBACK_CENTER: L.LatLngTuple = [21.0285, 105.8542]

const container = useTemplateRef<HTMLDivElement>('container')
const map = shallowRef<L.Map | null>(null)
let marker: L.Marker | null = null
let tiles: L.TileLayer | null = null
/** Đổi do chính bản đồ (bấm / kéo) — không kéo khung nhìn theo, tránh nhảy bản đồ khi người dùng đang thao tác. */
let fromMap = false
const { theme } = useTheme()
const placeStore = usePlaceStore()
const { locateStatus } = storeToRefs(placeStore)

function createTiles(current: typeof theme.value): L.TileLayer {
  const source = tileSource(current)
  container.value?.classList.toggle('ls-map--invert', source.invertInDark)
  return L.tileLayer(source.url, {
    maxZoom: source.maxZoom,
    subdomains: source.subdomains,
    attribution: source.attribution,
    tileSize: source.tileSize,
    zoomOffset: source.zoomOffset,
  })
}

/** Toạ độ làm tròn 6 chữ số (~0,1 m) — đủ chính xác, tránh chuỗi số dài vô nghĩa trong JSON / nháp. */
function round(point: L.LatLng): GeoPoint {
  return { lat: Number(point.lat.toFixed(6)), lng: Number(point.lng.toFixed(6)) }
}

function placeMarker(point: GeoPoint, pan: boolean): void {
  const m = map.value
  if (!m) return
  if (!marker) {
    marker = L.marker([point.lat, point.lng], {
      icon: L.divIcon({
        className: 'ls-pin-icon',
        html: '<span class="ls-pin ls-pin--active">Địa điểm ở đây</span>',
        iconSize: [0, 0],
      }),
      draggable: true,
      title: 'Vị trí địa điểm — kéo để chỉnh',
      alt: 'Vị trí địa điểm',
    }).addTo(m)
    marker.on('dragend', () => {
      if (marker) setFromMap(round(marker.getLatLng()))
    })
  } else {
    marker.setLatLng([point.lat, point.lng])
  }
  if (pan) m.setView([point.lat, point.lng], Math.max(m.getZoom(), 17))
}

function setFromMap(point: GeoPoint): void {
  fromMap = true
  location.value = point
}

async function useMyLocation(): Promise<void> {
  const here = await placeStore.locate()
  if (here) location.value = { lat: Number(here.lat.toFixed(6)), lng: Number(here.lng.toFixed(6)) }
}

onMounted(() => {
  const el = container.value
  if (!el) return
  const start = location.value
  const m = L.map(el, {
    center: start ? [start.lat, start.lng] : FALLBACK_CENTER,
    zoom: start ? 17 : 13,
  })
  tiles = createTiles(theme.value).addTo(m)
  m.on('click', (event: L.LeafletMouseEvent) => setFromMap(round(event.latlng)))
  map.value = m
  if (start) placeMarker(start, false)
})

onBeforeUnmount(() => {
  map.value?.remove()
  map.value = null
  marker = null
})

watch(theme, (current) => {
  if (!map.value) return
  tiles?.remove()
  tiles = createTiles(current).addTo(map.value)
})

// Đổi từ ngoài (vị trí của tôi, khôi phục nháp) → đặt ghim và đưa khung nhìn tới đó
watch(location, (point) => {
  if (point) placeMarker(point, !fromMap)
  fromMap = false
})
</script>

<template>
  <div class="flex flex-col gap-2">
    <div
      ref="container"
      class="ls-map h-[320px] overflow-hidden rounded-md"
      role="region"
      aria-label="Bản đồ chọn vị trí — bấm để đặt ghim, kéo ghim để chỉnh"
    ></div>
    <div class="flex flex-wrap items-center gap-3">
      <LsButton
        variant="ghost"
        size="sm"
        :loading="locateStatus === 'locating'"
        @click="useMyLocation"
        >Vị trí của tôi</LsButton
      >
      <span class="text-caption text-ink-muted" aria-live="polite">
        <template v-if="location"
          >Đã chọn: {{ location.lat.toFixed(5) }}, {{ location.lng.toFixed(5) }}</template
        >
        <template v-else>Bấm lên bản đồ để đặt ghim vào đúng chỗ.</template>
      </span>
    </div>
    <p v-if="locateStatus === 'denied'" class="m-0 text-caption" role="alert">
      ✕ Chưa được cấp quyền vị trí — hãy bấm lên bản đồ để chọn.
    </p>
  </div>
</template>
