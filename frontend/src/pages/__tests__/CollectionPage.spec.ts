import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { CollectionDetail, MeResponse, PlaceSummary } from '@/types/api'

import CollectionPage from '../CollectionPage.vue'

const api = {
  get: vi.fn(),
  shared: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  addPlace: vi.fn(),
  removePlace: vi.fn(),
}
vi.mock('@/api/collections', () => ({
  collectionsApi: new Proxy(
    {},
    {
      get:
        (_, name: string) =>
        (...args: unknown[]) =>
          api[name as keyof typeof api](...args),
    },
  ),
}))
vi.mock('@/plugins/toast', () => ({
  useNotify: () => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }),
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

function place(id: number, status: PlaceSummary['status'] = 'APPROVED'): PlaceSummary {
  return {
    id,
    slug: `quan-${id}`,
    name: `Quán số ${id}`,
    category: { id: 11, name: 'Phở & bún', slug: 'pho-bun' },
    address: `${id} Hàng Gà`,
    city: 'Hà Nội',
    location: { lat: 21, lng: 105 },
    coverPhoto: null,
    priceMin: null,
    priceMax: null,
    bayesianScore: 4,
    avgRating: 4,
    reviewCount: 3,
    status,
    distanceM: null,
    tags: [],
  }
}

const COLLECTION: CollectionDetail = {
  id: 7,
  name: 'Quán ăn Hà Nội nên thử',
  isPublic: true,
  shareSlug: 'quan-an-ha-noi-nen-thu-x7k2qa',
  placeCount: 2,
  coverPhoto: null,
  description: 'Đi ăn cuối tuần.',
  owner: { id: 42, displayName: 'Minh Anh', avatarUrl: null },
  places: [
    { place: place(1), note: 'Gọi tái lăn', addedAt: '2026-10-10T03:00:00Z' },
    { place: place(2, 'HIDDEN'), note: null, addedAt: '2026-10-09T03:00:00Z' },
  ],
}

let router: Router

async function mountPage(props: { id?: number; shareSlug?: string }): Promise<VueWrapper> {
  const Empty = { render: () => null }
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Empty },
      { path: '/search', name: 'search', component: Empty },
      { path: '/places/:slug', name: 'place', component: Empty },
      { path: '/users/:id', name: 'profile', component: Empty },
      { path: '/collections/:id', name: 'collection', component: Empty },
      { path: '/c/:shareSlug', name: 'shared-collection', component: Empty },
    ],
  })
  await router.push(props.shareSlug ? `/c/${props.shareSlug}` : `/collections/${props.id}`)
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(CollectionPage, {
    props,
    global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

const buttons = (wrapper: VueWrapper) => wrapper.findAll('button').map((b) => b.text())

beforeEach(() => {
  setActivePinia(createPinia())
  Object.values(api).forEach((fn) => fn.mockReset())
  api.get.mockResolvedValue(structuredClone(COLLECTION))
  api.shared.mockResolvedValue({ ...structuredClone(COLLECTION), places: [COLLECTION.places[0]] })
})

describe('CollectionPage (UC18)', () => {
  it('chủ bộ: thông tin, link chia sẻ, công cụ sửa; địa điểm bị ẩn có nhãn', async () => {
    useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
    const wrapper = await mountPage({ id: 7 })
    expect(api.get).toHaveBeenCalledWith(7)
    expect(wrapper.get('h1').text()).toBe('Quán ăn Hà Nội nên thử')
    expect(wrapper.text()).toContain('2 địa điểm')
    expect(wrapper.text()).toContain('● Công khai')
    expect(wrapper.text()).toContain('Đi ăn cuối tuần.')
    const share = wrapper.get('input[readonly]').element as HTMLInputElement
    expect(share.value).toMatch(/\/c\/quan-an-ha-noi-nen-thu-x7k2qa$/)
    expect(buttons(wrapper)).toEqual(expect.arrayContaining(['Sửa', 'Xoá bộ sưu tập', 'Sao chép']))
    expect(wrapper.text()).toContain('Gọi tái lăn')
    expect(wrapper.text()).toContain('✕ Đã bị ẩn — người khác không thấy')
  })

  it('bỏ địa điểm khỏi bộ và sửa ghi chú (PUT idempotent)', async () => {
    useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
    api.removePlace.mockResolvedValue(undefined)
    api.addPlace.mockResolvedValue(undefined)
    const wrapper = await mountPage({ id: 7 })

    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Bỏ khỏi bộ')!
      .trigger('click')
    await flushPromises()
    expect(api.removePlace).toHaveBeenCalledWith(7, 1)

    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Sửa ghi chú')!
      .trigger('click')
    await wrapper.get('textarea').setValue('  Gọi tái chín  ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.addPlace).toHaveBeenCalledWith(7, 1, 'Gọi tái chín')
  })

  it('xoá bộ phải xác nhận, xong về tab Bộ sưu tập của trang cá nhân', async () => {
    useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
    api.remove.mockResolvedValue(undefined)
    const wrapper = await mountPage({ id: 7 })
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Xoá bộ sưu tập')!
      .trigger('click')
    expect(api.remove).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('Xoá bộ này?')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Xoá')!
      .trigger('click')
    await flushPromises()
    expect(api.remove).toHaveBeenCalledWith(7)
    expect(router.currentRoute.value.fullPath).toBe('/users/42?tab=collections')
  })

  it('link chia sẻ: chỉ đọc, kể cả khi chủ tự mở', async () => {
    useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
    const wrapper = await mountPage({ shareSlug: 'quan-an-ha-noi-nen-thu-x7k2qa' })
    expect(api.shared).toHaveBeenCalledWith('quan-an-ha-noi-nen-thu-x7k2qa')
    expect(wrapper.get('h1').text()).toBe('Quán ăn Hà Nội nên thử')
    expect(wrapper.text()).toContain('Minh Anh')
    expect(buttons(wrapper)).not.toContain('Sửa')
    expect(buttons(wrapper)).not.toContain('Bỏ khỏi bộ')
    expect(wrapper.find('input[readonly]').exists()).toBe(false)
  })

  it('bộ công khai của người khác qua /collections/:id: chỉ đọc', async () => {
    useAuthStore().$patch({ user: { ...ME, id: 99 }, accessToken: 'token' })
    const wrapper = await mountPage({ id: 7 })
    expect(buttons(wrapper)).not.toContain('Xoá bộ sưu tập')
    expect(wrapper.text()).not.toContain('● Công khai')
  })

  it('riêng tư / không tồn tại / link đã thu hồi → trang không tìm thấy', async () => {
    const config = { headers: {} } as InternalAxiosRequestConfig
    api.shared.mockRejectedValue(
      new AxiosError('x', 'ERR', config, null, {
        data: { code: 'COLLECTION_NOT_FOUND' },
        status: 404,
        statusText: '',
        headers: {},
        config,
      }),
    )
    const wrapper = await mountPage({ shareSlug: 'link-cu-abc123' })
    expect(wrapper.text()).toContain('Không tìm thấy trang này')
  })
})
