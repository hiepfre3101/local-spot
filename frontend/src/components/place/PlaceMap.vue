<script setup lang="ts">
import 'leaflet/dist/leaflet.css'
import 'leaflet.markercluster/dist/MarkerCluster.css'

import * as L from 'leaflet'
import 'leaflet.markercluster'
import { onBeforeUnmount, onMounted, shallowRef, useTemplateRef, watch } from 'vue'

import { useTheme } from '@/composables/useTheme'
import type { GeoPoint, PlaceSummary } from '@/types/api'
import { formatNumber } from '@/utils/format'
import { radiusForView } from '@/utils/geo'
import { tileSource } from '@/utils/mapTiles'
import { scoreText } from '@/utils/rating'

/**
 * Bản đồ Leaflet + OpenStreetMap (plan §2). Ghim là pill `olive` mang điểm (P02 — chốt "ghim hiển thị điểm"), ghim đang
 * chọn `coral`; vị trí người dùng là chấm `sea`. Ghim gần nhau gom cụm (leaflet.markercluster) — nhãn cụm là số địa
 * điểm bằng chữ, không chỉ bằng màu.
 *
 * Ghim là phần tử Leaflet có thể focus (Tab) và Enter để chọn, kèm `title` là tên địa điểm. Chỉ báo `moved` khi
 * **người dùng** kéo / thu phóng (để hiện "Tìm trong vùng này"), không báo khi bản đồ tự canh theo kết quả.
 */
const props = withDefaults(
  defineProps<{
    places: readonly PlaceSummary[]
    activeId?: number | null
    userLocation?: GeoPoint | null
    /** Đổi giá trị → canh lại khung nhìn theo kết quả (kết quả mới, không phải mỗi lần tải thêm trang). */
    fitKey?: string
    /** Bản đồ nhỏ của P03: một ghim mang tên, không gom cụm, không báo di chuyển. */
    compact?: boolean
    /** Nhãn ghim: điểm (P02) hay tên địa điểm (bản đồ chỉ đường). `compact` luôn dùng tên. */
    pinLabel?: 'score' | 'name'
    /** Đường đi vẽ trên bản đồ (hộp thoại chỉ đường) — đổi thì canh lại khung nhìn cho thấy cả tuyến. */
    route?: readonly GeoPoint[] | null
  }>(),
  {
    activeId: null,
    userLocation: null,
    fitKey: '',
    compact: false,
    pinLabel: 'score',
    route: null,
  },
)
const emit = defineEmits<{
  select: [placeId: number]
  moved: [view: { center: GeoPoint; radius: number }]
}>()

/** Trung tâm Hà Nội — khung nhìn khi chưa có kết quả / vị trí. */
const FALLBACK_CENTER: L.LatLngExpression = [21.0285, 105.8542]

const container = useTemplateRef<HTMLDivElement>('container')
const map = shallowRef<L.Map | null>(null)
let layer: L.FeatureGroup | null = null
let meMarker: L.Marker | null = null
const markers = new Map<number, L.Marker>()
let programmaticMove = false
let resizeObserver: ResizeObserver | null = null
let tiles: L.TileLayer | null = null
let routeLine: L.Polyline | null = null
const { theme } = useTheme()

/** Lớp nền theo theme (MapTiler Dataviz / Dataviz Dark) — đổi theme thì thay lớp; nguồn dự phòng không có bản tối thì đảo màu. */
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

function pinIcon(place: PlaceSummary, active: boolean): L.DivIcon {
  const label =
    props.compact || props.pinLabel === 'name'
      ? escapeHtml(place.name)
      : place.reviewCount > 0
        ? scoreText(place.bayesianScore)
        : '—'
  return L.divIcon({
    className: 'ls-pin-icon',
    html: `<span class="ls-pin${active ? ' ls-pin--active' : ''}">${label}</span>`,
    iconSize: [0, 0],
  })
}

function renderMarkers(): void {
  if (!map.value || !layer) return
  layer.clearLayers()
  markers.clear()
  for (const place of props.places) {
    const marker = L.marker([place.location.lat, place.location.lng], {
      icon: pinIcon(place, place.id === props.activeId),
      title:
        place.reviewCount > 0
          ? `${place.name} — ${scoreText(place.bayesianScore)} điểm`
          : place.name,
      alt: place.name,
      keyboard: !props.compact,
      interactive: !props.compact,
      riseOnHover: true,
    })
    marker.on('click', () => emit('select', place.id))
    markers.set(place.id, marker)
    layer.addLayer(marker)
  }
}

function renderMe(): void {
  if (!map.value) return
  meMarker?.remove()
  meMarker = null
  if (props.userLocation) {
    meMarker = L.marker([props.userLocation.lat, props.userLocation.lng], {
      icon: L.divIcon({
        className: 'ls-pin-icon',
        html: '<span class="ls-pin ls-pin--me"></span>',
        iconSize: [0, 0],
      }),
      title: 'Vị trí của bạn',
      alt: 'Vị trí của bạn',
      keyboard: false,
      interactive: false,
      zIndexOffset: 1000,
    }).addTo(map.value)
  }
}

/** Tuyến đường màu `sea` (màu liên kết / thông tin của design system), nét liền, không bóng. */
function renderRoute(): void {
  const m = map.value
  if (!m) return
  routeLine?.remove()
  routeLine = null
  if (props.route && props.route.length > 1) {
    const color =
      getComputedStyle(document.documentElement).getPropertyValue('--sea').trim() || '#2c6a6e'
    routeLine = L.polyline(
      props.route.map((p) => [p.lat, p.lng] as L.LatLngTuple),
      { color, weight: 5, opacity: 0.9, interactive: false },
    ).addTo(m)
  }
}

function fit(): void {
  const m = map.value
  if (!m) return
  const points: L.LatLngExpression[] = props.places.map((p) => [p.location.lat, p.location.lng])
  if (props.userLocation) points.push([props.userLocation.lat, props.userLocation.lng])
  for (const p of props.route ?? []) points.push([p.lat, p.lng])
  programmaticMove = true
  if (points.length === 0) {
    m.setView(FALLBACK_CENTER, 13)
  } else if (points.length === 1) {
    m.setView(points[0]!, props.compact ? 16 : 15)
  } else {
    m.fitBounds(L.latLngBounds(points), { padding: [40, 40], maxZoom: 16 })
  }
  // moveend của lần canh này phát sau khi hàm trả về — bỏ cờ ở đó
}

onMounted(() => {
  const el = container.value
  if (!el) return
  const m = L.map(el, {
    center: FALLBACK_CENTER,
    zoom: 13,
    zoomControl: !props.compact,
    scrollWheelZoom: !props.compact,
    dragging: !props.compact || !L.Browser.mobile,
    attributionControl: true,
  })
  tiles = createTiles(theme.value).addTo(m)
  layer = props.compact
    ? L.featureGroup().addTo(m)
    : L.markerClusterGroup({
        showCoverageOnHover: false,
        maxClusterRadius: 48,
        spiderfyOnMaxZoom: true,
        iconCreateFunction: (cluster) =>
          L.divIcon({
            className: 'ls-pin-icon',
            html: `<span class="ls-pin">${formatNumber(cluster.getChildCount())} địa điểm</span>`,
            iconSize: [0, 0],
          }),
      }).addTo(m)
  m.on('moveend', () => {
    if (programmaticMove) {
      programmaticMove = false
      return
    }
    if (props.compact) return
    const center = m.getCenter()
    const bounds = m.getBounds()
    emit('moved', {
      center: { lat: center.lat, lng: center.lng },
      radius: radiusForView(
        { lat: center.lat, lng: center.lng },
        bounds.getNorth(),
        bounds.getEast(),
      ),
    })
  })
  map.value = m
  renderMarkers()
  renderMe()
  renderRoute()
  fit()
  resizeObserver = new ResizeObserver(() => m.invalidateSize())
  resizeObserver.observe(el)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  map.value?.remove()
  map.value = null
  layer = null
  tiles = null
  routeLine = null
  markers.clear()
})

watch(theme, (current) => {
  const m = map.value
  if (!m) return
  tiles?.remove()
  tiles = createTiles(current).addTo(m)
  renderRoute() // màu `sea` đổi theo theme
})
watch(
  () => props.route,
  () => {
    renderRoute()
    fit()
  },
)
watch(
  () => props.places,
  () => renderMarkers(),
)
watch(
  () => props.userLocation,
  () => {
    renderMe()
    if (props.pinLabel === 'name') fit() // bản đồ chỉ đường: thấy cả bạn lẫn địa điểm
  },
)
watch(
  () => props.fitKey,
  () => fit(),
)
watch(
  () => props.activeId,
  (now, before) => {
    for (const id of [before, now]) {
      if (id === null || id === undefined) continue
      const place = props.places.find((p) => p.id === id)
      const marker = markers.get(id)
      if (place && marker) {
        marker.setIcon(pinIcon(place, id === now))
        marker.setZIndexOffset(id === now ? 500 : 0)
      }
    }
  },
)

function escapeHtml(text: string): string {
  return text.replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!,
  )
}
</script>

<template>
  <div ref="container" class="ls-map" role="region" aria-label="Bản đồ địa điểm"></div>
</template>
