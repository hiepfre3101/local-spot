import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  RouteNotFoundError,
  fetchRoute,
  formatDuration,
  routeUrl,
  routingBaseUrl,
} from '../routing'

const FROM = { lat: 21.0285, lng: 105.8542 }
const TO = { lat: 21.0175, lng: 105.8523 }

function stubFetch(status: number, body: unknown) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('routing (OSRM)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('URL theo hồ sơ phương tiện, tọa độ "kinh độ,vĩ độ", đổi máy chủ bằng biến môi trường', () => {
    expect(routeUrl(FROM, TO, 'drive', 'https://r.test')).toBe(
      'https://r.test/routed-car/route/v1/driving/105.8542,21.0285;105.8523,21.0175' +
        '?overview=full&geometries=geojson&alternatives=false&steps=false',
    )
    expect(routeUrl(FROM, TO, 'walk', 'https://r.test')).toContain('/routed-foot/route/v1/foot/')
    expect(routingBaseUrl({} as ImportMetaEnv)).toBe('https://routing.openstreetmap.de')
    expect(
      routingBaseUrl({ VITE_ROUTING_URL: 'https://osrm.localspot.vn/' } as ImportMetaEnv),
    ).toBe('https://osrm.localspot.vn')
  })

  it('đổi GeoJSON [lng, lat] thành điểm {lat, lng}', async () => {
    stubFetch(200, {
      code: 'Ok',
      routes: [
        {
          distance: 1520.4,
          duration: 312.7,
          geometry: {
            coordinates: [
              [105.8542, 21.0285],
              [105.853, 21.02],
              [105.8523, 21.0175],
            ],
          },
        },
      ],
    })
    const route = await fetchRoute(FROM, TO, 'drive')
    expect(route.distanceM).toBe(1520.4)
    expect(route.durationS).toBe(312.7)
    expect(route.path).toEqual([FROM, { lat: 21.02, lng: 105.853 }, TO])
  })

  it('không có đường → RouteNotFoundError; máy chủ lỗi → lỗi thường', async () => {
    stubFetch(400, { code: 'NoRoute', message: 'Impossible route' })
    await expect(fetchRoute(FROM, TO, 'walk')).rejects.toBeInstanceOf(RouteNotFoundError)

    stubFetch(503, { code: 'Busy' })
    const error = await fetchRoute(FROM, TO, 'walk').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(Error)
    expect(error).not.toBeInstanceOf(RouteNotFoundError)
  })

  it('thời gian làm tròn lên phút', () => {
    expect(formatDuration(5)).toBe('1 phút')
    expect(formatDuration(312.7)).toBe('6 phút')
    expect(formatDuration(3600)).toBe('1 giờ')
    expect(formatDuration(3900)).toBe('1 giờ 5 phút')
  })
})
