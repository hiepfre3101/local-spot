/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Key MapTiler (gói miễn phí) cho lớp nền bản đồ — thiếu thì dùng OSM Đức, xem `src/utils/mapTiles.ts`. */
  readonly VITE_MAPTILER_KEY?: string
  /** Thay hẳn nhà cung cấp lớp nền (Leaflet URL template). */
  readonly VITE_MAP_TILE_URL?: string
  readonly VITE_MAP_TILE_URL_DARK?: string
  readonly VITE_MAP_TILE_ATTRIBUTION?: string
  /** Máy chủ OSRM cho chỉ đường — mặc định routing.openstreetmap.de, xem `src/utils/routing.ts`. */
  readonly VITE_ROUTING_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
