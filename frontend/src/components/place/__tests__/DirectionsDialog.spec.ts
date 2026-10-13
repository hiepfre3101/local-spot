import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'

import type { PlaceDetail } from '@/types/api'

import DirectionsDialog from '../DirectionsDialog.vue'

const fetchRoute = vi.fn()

vi.mock('@/utils/routing', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/routing')>()),
  fetchRoute: (...args: unknown[]) => fetchRoute(...args),
}))
vi.mock('../PlaceMap.vue', () => ({
  __esModule: true,
  default: { name: 'PlaceMap', props: ['route', 'userLocation'], render: () => null },
}))

const PLACE = {
  id: 5,
  name: 'Phở Thìn',
  address: '13 Lò Đúc',
  location: { lat: 21.0175, lng: 105.8523 },
} as PlaceDetail

function mockGeolocation(result: 'ok' | 'denied') {
  vi.stubGlobal('navigator', {
    ...navigator,
    geolocation: {
      getCurrentPosition: (ok: PositionCallback, fail: PositionErrorCallback) =>
        result === 'ok'
          ? ok({ coords: { latitude: 21.0285, longitude: 105.8542 } } as GeolocationPosition)
          : fail({ code: 1, PERMISSION_DENIED: 1 } as GeolocationPositionError),
    },
  })
}

async function mountAndOpen() {
  const wrapper = mount(DirectionsDialog, {
    props: { place: PLACE },
    attachTo: document.body,
    global: {
      plugins: [
        [
          VueQueryPlugin,
          { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) },
        ],
      ],
    },
  })
  ;(wrapper.vm as unknown as { open: () => void }).open()
  await flushPromises()
  await flushPromises()
  return wrapper
}

describe('DirectionsDialog', () => {
  beforeAll(() => {
    // jsdom chưa cài <dialog>.showModal / close
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true
    }
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.open = false
      this.dispatchEvent(new Event('close'))
    }
  })
  beforeEach(() => {
    setActivePinia(createPinia())
    fetchRoute.mockReset().mockResolvedValue({
      distanceM: 1520,
      durationS: 312,
      path: [
        { lat: 21.0285, lng: 105.8542 },
        { lat: 21.0175, lng: 105.8523 },
      ],
    })
  })
  afterEach(() => vi.unstubAllGlobals())

  it('mở → hỏi vị trí → vẽ tuyến, hiện thời gian và quãng đường; đổi sang đi bộ tính lại', async () => {
    mockGeolocation('ok')
    const wrapper = await mountAndOpen()

    expect(fetchRoute).toHaveBeenCalledWith(
      { lat: 21.0285, lng: 105.8542 },
      PLACE.location,
      'drive',
      expect.anything(),
    )
    expect(wrapper.text()).toContain('6 phút')
    expect(wrapper.text()).toContain('1,5 km')
    expect(wrapper.findComponent({ name: 'PlaceMap' }).props('route')).toHaveLength(2)

    await wrapper
      .findAll('button.ls-fchip')
      .find((b) => b.text() === 'Đi bộ')!
      .trigger('click')
    await flushPromises()
    expect(fetchRoute).toHaveBeenLastCalledWith(
      expect.anything(),
      PLACE.location,
      'walk',
      expect.anything(),
    )
    wrapper.unmount()
  })

  it('bị chặn quyền vị trí: không gọi chỉ đường, vẫn có bản đồ + địa chỉ', async () => {
    mockGeolocation('denied')
    const wrapper = await mountAndOpen()

    expect(fetchRoute).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Bạn đã chặn quyền vị trí')
    expect(wrapper.text()).toContain('13 Lò Đúc')
    expect(wrapper.findComponent({ name: 'PlaceMap' }).exists()).toBe(true)
    wrapper.unmount()
  })

  it('dịch vụ chỉ đường lỗi: báo lỗi kèm khoảng cách đường chim bay', async () => {
    mockGeolocation('ok')
    fetchRoute.mockRejectedValue(new Error('Dịch vụ chỉ đường lỗi (503)'))
    const wrapper = await mountAndOpen()

    // Lỗi mạng / máy chủ được thử lại một lần (~1 giây) trước khi báo
    await vi.waitFor(() => expect(wrapper.text()).toContain('Chưa tải được đường đi'), {
      timeout: 4000,
    })
    expect(fetchRoute).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toMatch(/Cách bạn khoảng 1,2 km theo đường chim bay/)
    wrapper.unmount()
  })
})
