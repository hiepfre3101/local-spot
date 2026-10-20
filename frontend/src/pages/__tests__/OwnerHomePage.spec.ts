import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { defineComponent, h } from 'vue'

import { useAuthStore } from '@/stores/auth'
import type { MeResponse, MyClaim, PlaceDetail, PlaceSummary } from '@/types/api'

import OwnerHomePage from '../owner/OwnerHomePage.vue'

const owner = { places: vi.fn(), myClaims: vi.fn(), claim: vi.fn() }
vi.mock('@/api/owner', () => ({
  ownerApi: {
    places: (...a: unknown[]) => owner.places(...a),
    myClaims: (...a: unknown[]) => owner.myClaims(...a),
    claim: (...a: unknown[]) => owner.claim(...a),
  },
}))
const places = { detail: vi.fn(), search: vi.fn() }
vi.mock('@/api/places', () => ({
  placesApi: {
    detail: (...a: unknown[]) => places.detail(...a),
    search: (...a: unknown[]) => places.search(...a),
  },
}))
vi.mock('@/api/me', () => ({ meApi: { get: () => new Promise(() => undefined) } }))
vi.mock('@/plugins/toast', () => ({
  useNotify: () => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }),
}))
// Chọn ảnh thật cần canvas — thay bằng nút thêm một ảnh giả
vi.mock('@/components/review/PhotoPicker.vue', () => ({
  default: defineComponent({
    props: { modelValue: { type: Array, required: true } },
    emits: ['update:modelValue'],
    setup(_, { emit }) {
      return () =>
        h(
          'button',
          {
            type: 'button',
            onClick: () =>
              emit('update:modelValue', [
                { key: 'k1', file: new File(['x'], 'giay-phep.jpg'), url: 'blob:1' },
              ]),
          },
          'Thêm ảnh giả',
        )
    },
  }),
}))

const ME: MeResponse = {
  id: 42,
  displayName: 'Minh Anh',
  avatarUrl: null,
  email: 'minh.anh@localspot.test',
  bio: null,
  emailVerified: true,
  roles: ['USER'],
  permissions: [],
  trustScore: 40,
  ownedPlaceIds: [],
}

const PLACE = {
  id: 5,
  slug: 'pho-thin-ha-noi',
  name: 'Phở Thìn',
  address: '13 Lò Đúc',
  city: 'Hà Nội',
  status: 'APPROVED',
  owner: null,
  claimable: true,
} as unknown as PlaceDetail

function claim(id: number, overrides: Partial<MyClaim> = {}): MyClaim {
  return {
    id,
    place: { id: 9, slug: 'ca-phe-giang', name: 'Cà phê Giảng' },
    status: 'PENDING',
    rejectReason: null,
    createdAt: '2026-10-10T03:00:00Z',
    reviewedAt: null,
    ...overrides,
  }
}

async function mountPage(query = ''): Promise<VueWrapper> {
  const Empty = { render: () => null }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Empty },
      { path: '/owner', name: 'owner', component: Empty },
      { path: '/owner/places/:id', name: 'owner-place', component: Empty },
      { path: '/places/:slug', name: 'place', component: Empty },
      { path: '/propose', name: 'propose', component: Empty },
    ],
  })
  await router.push(`/owner${query}`)
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(OwnerHomePage, {
    global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  setActivePinia(createPinia())
  useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
  Object.values({ ...owner, ...places }).forEach((fn) => fn.mockReset())
  owner.myClaims.mockResolvedValue({ items: [], nextCursor: null })
  places.detail.mockResolvedValue(PLACE)
})

describe('OwnerHomePage (P09 /owner)', () => {
  it('chưa sở hữu gì: không gọi API của chủ, hiện form yêu cầu sở hữu', async () => {
    const wrapper = await mountPage()
    expect(owner.places).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('Đang quản lý')
    expect(wrapper.text()).toContain('Yêu cầu xác nhận sở hữu')
    expect(wrapper.find('input[type="search"]').exists()).toBe(true)
  })

  it('chủ địa điểm: danh sách địa điểm dẫn tới trang quản lý; form mở bằng nút', async () => {
    useAuthStore().$patch({
      user: { ...ME, permissions: ['place:update-own'], ownedPlaceIds: [5] },
    })
    owner.places.mockResolvedValue([
      {
        id: 5,
        slug: 'pho-thin-ha-noi',
        name: 'Phở Thìn',
        address: '13 Lò Đúc',
        bayesianScore: 4.4,
        reviewCount: 10,
      } as PlaceSummary,
    ])
    const wrapper = await mountPage()
    const link = wrapper.findAll('a').find((a) => a.text() === 'Phở Thìn')!
    expect(link.attributes('href')).toBe('/owner/places/5')
    expect(wrapper.find('input[type="search"]').exists()).toBe(false)
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '+ Yêu cầu sở hữu địa điểm khác')!
      .trigger('click')
    expect(wrapper.find('input[type="search"]').exists()).toBe(true)
  })

  it('yêu cầu đã gửi: trạng thái bằng ký hiệu + chữ, lý do từ chối, gửi lại', async () => {
    owner.myClaims.mockResolvedValue({
      items: [
        claim(2),
        claim(1, {
          status: 'REJECTED',
          rejectReason: 'Ảnh giấy phép mờ.',
          reviewedAt: '2026-10-12T03:00:00Z',
        }),
      ],
      nextCursor: null,
    })
    const wrapper = await mountPage()
    const text = wrapper.text()
    expect(text).toContain('● Đang chờ duyệt')
    expect(text).toContain('✕ Bị từ chối')
    expect(text).toContain('Lý do: Ảnh giấy phép mờ.')
    expect(text).toContain('Xét 12/10/2026')

    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Gửi lại')!
      .trigger('click')
    await flushPromises()
    expect(places.detail).toHaveBeenCalledWith('ca-phe-giang')
  })

  it('đến từ trang địa điểm: chọn sẵn, kiểm điện thoại + minh chứng rồi gửi multipart', async () => {
    owner.claim.mockResolvedValue(undefined)
    const wrapper = await mountPage('?place=pho-thin-ha-noi')
    expect(places.detail).toHaveBeenCalledWith('pho-thin-ha-noi')
    expect(wrapper.text()).toContain('Phở Thìn')

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(owner.claim).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Số điện thoại không hợp lệ')
    expect(wrapper.text()).toContain('Cần ít nhất một ảnh minh chứng.')

    await wrapper.get('input[type="tel"]').setValue('0912 345 678')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Thêm ảnh giả')!
      .trigger('click')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(owner.claim).toHaveBeenCalledWith(5, { contactPhone: '0912 345 678', note: undefined }, [
      expect.objectContaining({ fileName: 'giay-phep.jpg' }),
    ])
  })

  it('địa điểm đã có chủ / mình đang chờ duyệt → không có form gửi', async () => {
    places.detail.mockResolvedValue({ ...PLACE, claimable: false, owner: { id: 1 } })
    let wrapper = await mountPage('?place=pho-thin-ha-noi')
    expect(wrapper.text()).toContain('Địa điểm này đã có chủ được xác nhận.')
    expect(wrapper.find('input[type="tel"]').exists()).toBe(false)
    wrapper.unmount()

    places.detail.mockResolvedValue({ ...PLACE, claimable: false })
    wrapper = await mountPage('?place=pho-thin-ha-noi')
    expect(wrapper.text()).toContain('Bạn đã có một yêu cầu đang chờ duyệt cho địa điểm này.')
  })

  it('409 từ máy chủ hiện câu dễ hiểu theo mã lỗi', async () => {
    const config = { headers: {} } as InternalAxiosRequestConfig
    owner.claim.mockRejectedValue(
      new AxiosError('x', 'ERR', config, null, {
        data: { code: 'CLAIM_ALREADY_PENDING', detail: 'x' },
        status: 409,
        statusText: '',
        headers: {},
        config,
      }),
    )
    const wrapper = await mountPage('?place=pho-thin-ha-noi')
    await wrapper.get('input[type="tel"]').setValue('0912345678')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Thêm ảnh giả')!
      .trigger('click')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Bạn đã có một yêu cầu đang chờ duyệt cho địa điểm này.')
  })
})
