import { describe, expect, it } from 'vitest'

import { tileSource } from '../mapTiles'

const env = (values: Partial<ImportMetaEnv>) => values as ImportMetaEnv

describe('tileSource', () => {
  it('có key MapTiler: Dataviz (Sáng) / Dataviz Dark (Tối), ô 512 px, ghi công MapTiler + OSM', () => {
    const light = tileSource('light', env({ VITE_MAPTILER_KEY: ' abc123 ' }))
    expect(light.url).toBe('https://api.maptiler.com/maps/dataviz/{z}/{x}/{y}.png?key=abc123')
    expect(light).toMatchObject({ tileSize: 512, zoomOffset: -1, invertInDark: false })
    expect(light.attribution).toContain('MapTiler')
    expect(light.attribution).toContain('OpenStreetMap')
    expect(tileSource('dark', env({ VITE_MAPTILER_KEY: 'abc123' })).url).toContain('/dataviz-dark/')
  })

  it('chưa có key: OSM Đức (không cần key), theme Tối đảo màu lớp nền', () => {
    expect(tileSource('light', env({}))).toMatchObject({
      url: 'https://tile.openstreetmap.de/{z}/{x}/{y}.png',
      invertInDark: false,
    })
    expect(tileSource('dark', env({ VITE_MAPTILER_KEY: '  ' })).invertInDark).toBe(true)
  })

  it('URL riêng thắng MapTiler; theme Tối dùng URL tối riêng nếu có, không thì đảo màu', () => {
    const custom = env({
      VITE_MAPTILER_KEY: 'abc123',
      VITE_MAP_TILE_URL: 'https://tiles.example.vn/{z}/{x}/{y}.png',
      VITE_MAP_TILE_ATTRIBUTION: '&copy; Example',
    })
    expect(tileSource('light', custom)).toMatchObject({
      url: 'https://tiles.example.vn/{z}/{x}/{y}.png',
      attribution: '&copy; Example',
    })
    expect(tileSource('dark', custom).invertInDark).toBe(true)
    const withDark = {
      ...custom,
      VITE_MAP_TILE_URL_DARK: 'https://tiles.example.vn/dark/{z}/{x}/{y}.png',
    }
    expect(tileSource('dark', withDark)).toMatchObject({ invertInDark: false })
    expect(tileSource('dark', withDark).url).toContain('/dark/')
  })
})
