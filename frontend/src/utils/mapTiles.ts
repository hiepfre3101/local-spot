import type { Theme } from '@/composables/useTheme'

export interface TileSource {
  url: string
  attribution: string
  subdomains: string
  maxZoom: number
  tileSize: number
  zoomOffset: number
  /** Lớp nền nhiều màu không có bản tối → đảo màu bằng CSS ở theme Tối (chỉ lớp nền, ghim giữ màu token). */
  invertInDark: boolean
}

const OSM = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'

/**
 * Lớp nền bản đồ (chốt 2026-10-13, sau hai lần chạy thử):
 *
 * 1. `tile.openstreetmap.org` bị DNS của một số nhà mạng Việt Nam chặn → bản đồ trắng.
 * 2. CARTO basemaps nay bắt buộc API key (trả ảnh "API KEY REQUIRED" với mã 200).
 *
 * → Mặc định **MapTiler** với key miễn phí (`VITE_MAPTILER_KEY` trong `frontend/.env`): kiểu **Dataviz** (Sáng) /
 * **Dataviz Dark** (Tối) — phẳng, ít màu, hợp design system; nhãn tiếng Việt từ dữ liệu OSM. Key ở frontend vốn công
 * khai (nằm trong bundle) — giới hạn theo tên miền trong trang quản lý của MapTiler.
 * Chưa có key → **OSM Đức** (`tile.openstreetmap.de`, máy chủ cộng đồng OSM, không cần key) để bản đồ không bao giờ
 * trắng khi dev. `VITE_MAP_TILE_URL` / `VITE_MAP_TILE_URL_DARK` / `VITE_MAP_TILE_ATTRIBUTION` thay hẳn nhà cung cấp.
 */
export function tileSource(theme: Theme, env: ImportMetaEnv = import.meta.env): TileSource {
  const custom =
    theme === 'dark' ? (env.VITE_MAP_TILE_URL_DARK ?? env.VITE_MAP_TILE_URL) : env.VITE_MAP_TILE_URL
  if (custom) {
    return {
      url: custom,
      attribution: env.VITE_MAP_TILE_ATTRIBUTION ?? OSM,
      subdomains: 'abc',
      maxZoom: 19,
      tileSize: 256,
      zoomOffset: 0,
      invertInDark: theme === 'dark' && !env.VITE_MAP_TILE_URL_DARK,
    }
  }
  const key = env.VITE_MAPTILER_KEY?.trim()
  if (key) {
    const style = theme === 'dark' ? 'dataviz-dark' : 'dataviz'
    return {
      // Ô 512 px (mặc định của MapTiler) + zoomOffset -1: nét như ô @2x, số request bằng 1/4 ô 256 px
      url: `https://api.maptiler.com/maps/${style}/{z}/{x}/{y}.png?key=${encodeURIComponent(key)}`,
      attribution: `&copy; <a href="https://www.maptiler.com/copyright/">MapTiler</a> ${OSM}`,
      subdomains: 'abc',
      maxZoom: 20,
      tileSize: 512,
      zoomOffset: -1,
      invertInDark: false,
    }
  }
  return {
    url: 'https://tile.openstreetmap.de/{z}/{x}/{y}.png',
    attribution: OSM,
    subdomains: 'abc',
    maxZoom: 19,
    tileSize: 256,
    zoomOffset: 0,
    invertInDark: theme === 'dark',
  }
}
