import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { MeResponse, PlaceSummary } from '@/types/api'
import { draftKey } from '@/utils/draft'
import { defaultWeek, parseTags, toOpeningHours } from '@/validation/place'

import ProposePlacePage from '../ProposePlacePage.vue'

const duplicates = vi.fn()
const propose = vi.fn()

vi.mock('@/api/places', () => ({
  placesApi: {
    duplicates: (...args: unknown[]) => duplicates(...args),
    propose: (...args: unknown[]) => propose(...args),
  },
}))
vi.mock('@/api/catalog', () => ({
  catalogApi: {
    categories: () =>
      Promise.resolve([
        {
          id: 1,
          name: 'Ăn uống',
          slug: 'an-uong',
          icon: null,
          children: [{ id: 11, name: 'Phở & bún', slug: 'pho-bun', icon: null, children: [] }],
        },
      ]),
    amenities: () =>
      Promise.resolve([
        { id: 7, name: 'Wi-Fi miễn phí', slug: 'wifi', icon: null },
        { id: 8, name: 'Chỗ gửi xe', slug: 'gui-xe', icon: null },
      ]),
  },
}))
// Leaflet không chạy được trong jsdom — thay bằng nút đặt ghim
vi.mock('@/components/place/LocationPicker.vue', () => ({
  default: defineComponent({
    props: { modelValue: { type: Object, default: null } },
    emits: ['update:modelValue'],
    setup(_, { emit }) {
      return () =>
        h(
          'button',
          {
            type: 'button',
            class: 'pick',
            onClick: () => emit('update:modelValue', { lat: 21.0175, lng: 105.8523 }),
          },
          'Đặt ghim',
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

const DUPLICATE: PlaceSummary = {
  id: 9,
  slug: 'pho-thin-lo-duc',
  name: 'Phở Thìn Lò Đúc',
  category: { id: 11, name: 'Phở & bún', slug: 'pho-bun' },
  address: '13 Lò Đúc',
  city: 'Hà Nội',
  location: { lat: 21.0176, lng: 105.8524 },
  coverPhoto: null,
  priceMin: null,
  priceMax: null,
  bayesianScore: 4.4,
  avgRating: 4.5,
  reviewCount: 10,
  status: 'APPROVED',
  distanceM: 12,
  tags: [],
}

/**
 * Node 26 có `localStorage` dựng sẵn (cần `--localstorage-file`) che mất bản của jsdom — thay bằng kho trong bộ nhớ, đúng
 * API `Storage` mà trang dùng.
 */
function memoryStorage(): Storage {
  const data = new Map<string, string>()
  return {
    get length() {
      return data.size
    },
    clear: () => data.clear(),
    getItem: (key) => data.get(key) ?? null,
    key: (index) => [...data.keys()][index] ?? null,
    removeItem: (key) => void data.delete(key),
    setItem: (key, value) => void data.set(key, String(value)),
  }
}

/** vee-validate kiểm schema bất đồng bộ; nghi trùng chờ 600 ms, nháp 800 ms — đồng hồ thật, chờ hết rồi mới kiểm. */
async function settle(ms = 0): Promise<void> {
  if (ms) await new Promise((resolve) => setTimeout(resolve, ms))
  for (let i = 0; i < 5; i++) {
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}

async function mountPage(): Promise<VueWrapper> {
  const Empty = { render: () => null }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Empty },
      { path: '/places/:slug', name: 'place', component: Empty },
      { path: '/propose', name: 'propose', component: ProposePlacePage },
    ],
  })
  await router.push('/propose')
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(ProposePlacePage, {
    global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
  })
  await settle()
  return wrapper
}

async function fillRequired(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('input[name="name"]').setValue('Phở Thìn')
  await wrapper.get('select').setValue('11')
  await wrapper.get('input[name="address"]').setValue('13 Lò Đúc')
  await wrapper.get('input[name="city"]').setValue('Hà Nội')
  await wrapper.get('button.pick').trigger('click')
}

async function submit(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('form').trigger('submit')
  await settle()
}

function httpError(status: number, data: object): AxiosError {
  const config = { headers: {} } as InternalAxiosRequestConfig
  return new AxiosError('x', 'ERR', config, null, {
    data,
    status,
    statusText: '',
    headers: {},
    config,
  })
}

describe('ProposePlacePage (P05)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
    vi.stubGlobal('localStorage', memoryStorage())
    duplicates.mockReset().mockResolvedValue([])
    propose.mockReset()
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('gửi đúng dữ liệu: thẻ tách + bỏ trùng, giờ mở cửa theo ngày đã bật, trường trống bỏ đi', async () => {
    propose.mockResolvedValue({ name: 'Phở Thìn', slug: 'pho-thin-ha-noi' })
    const wrapper = await mountPage()
    await fillRequired(wrapper)
    await wrapper.get('input[name="tags"]').setValue('Gia truyền, ăn sáng , gia truyền')
    await wrapper.get('input[type="checkbox"]').setValue(true) // Thứ Hai mở cửa
    await wrapper.get('input[value="8"]').setValue(true) // Chỗ gửi xe
    await submit(wrapper)

    expect(propose).toHaveBeenCalledWith(
      {
        name: 'Phở Thìn',
        categoryId: 11,
        description: undefined,
        address: '13 Lò Đúc',
        city: 'Hà Nội',
        location: { lat: 21.0175, lng: 105.8523 },
        priceMin: undefined,
        priceMax: undefined,
        phone: undefined,
        website: undefined,
        openingHours: [{ dayOfWeek: 1, openTime: '07:00', closeTime: '22:00' }],
        amenityIds: [8],
        tags: ['Gia truyền', 'ăn sáng'],
      },
      [],
    )
    expect(wrapper.get('h1').text()).toBe('Đã gửi — chờ kiểm duyệt')
    expect(localStorage.getItem(draftKey('propose-place', 42))).toBeNull()
  })

  it('thiếu trường bắt buộc → báo ngay; giá ngược → báo khi các trường khác đã hợp lệ; không gọi API', async () => {
    const wrapper = await mountPage()
    await wrapper.get('input[name="name"]').setValue('P')
    await submit(wrapper)
    const errors = () => wrapper.findAll('.ls-field__error').map((e) => e.text())
    expect(errors()).toEqual([
      '✕ Tên cần ít nhất 2 ký tự.',
      '✕ Chọn danh mục.',
      '✕ Nhập địa chỉ.',
      '✕ Nhập thành phố.',
      '✕ Chọn vị trí trên bản đồ.',
    ])

    // Luật so sánh hai giá chạy sau khi từng trường hợp lệ (zod) — backend cũng kiểm lại
    await fillRequired(wrapper)
    const [min, max] = wrapper.findAll('input[type="number"]')
    await min!.setValue('100000')
    await max!.setValue('50000')
    await submit(wrapper)
    expect(errors()).toEqual(['✕ Giá thấp nhất phải nhỏ hơn hoặc bằng giá cao nhất.'])
    expect(propose).not.toHaveBeenCalled()
  })

  it('nghi trùng (U7): hiện ứng viên, phải xác nhận "địa điểm khác" mới gửi được', async () => {
    duplicates.mockResolvedValue([DUPLICATE])
    propose.mockResolvedValue({ name: 'Phở Thìn', slug: 'pho-thin-ha-noi' })
    const wrapper = await mountPage()
    await fillRequired(wrapper)
    await settle(700)

    expect(duplicates).toHaveBeenLastCalledWith('Phở Thìn', { lat: 21.0175, lng: 105.8523 })
    expect(wrapper.text()).toContain('Phở Thìn Lò Đúc')
    expect(wrapper.text()).toContain('cách 10 m') // làm tròn tới 10 m

    await submit(wrapper)
    expect(propose).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('xác nhận đây là địa điểm khác')

    const confirm = wrapper
      .findAll('label.ls-check')
      .find((l) => l.text().includes('Đây là địa điểm khác'))!
    await confirm.get('input').setValue(true)
    await submit(wrapper)
    expect(propose).toHaveBeenCalled()
  })

  it('nháp: tự lưu khi nhập, khôi phục khi quay lại, bỏ nháp thì xoá', async () => {
    let wrapper = await mountPage()
    await wrapper.get('input[name="name"]').setValue('Bún chả Hương Liên')
    await wrapper.get('input[name="city"]').setValue('Hà Nội')
    await settle(900)
    expect(localStorage.getItem(draftKey('propose-place', 42))).toContain('Bún chả Hương Liên')
    wrapper.unmount()

    wrapper = await mountPage()
    expect(wrapper.text()).toContain('Đã khôi phục bản nháp')
    expect((wrapper.get('input[name="name"]').element as HTMLInputElement).value).toBe(
      'Bún chả Hương Liên',
    )

    const discard = wrapper.findAll('button').find((b) => b.text().includes('Bỏ nháp'))!
    await discard.trigger('click')
    await settle()
    expect(localStorage.getItem(draftKey('propose-place', 42))).toBeNull()
    expect((wrapper.get('input[name="name"]').element as HTMLInputElement).value).toBe('')
  })

  it('lỗi 422 của backend gắn vào ô; vị trí sai → nhắc đặt lại ghim', async () => {
    propose.mockRejectedValue(
      httpError(422, {
        code: 'VALIDATION_FAILED',
        detail: 'Dữ liệu không hợp lệ.',
        errors: [
          { field: 'website', message: 'phải là địa chỉ http(s)://' },
          { field: 'location.lat', message: 'phải nhỏ hơn hoặc bằng 90' },
        ],
      }),
    )
    const wrapper = await mountPage()
    await fillRequired(wrapper)
    await submit(wrapper)

    expect(wrapper.text()).toContain('phải là địa chỉ http(s)://')
    expect(wrapper.text()).toContain('Vị trí chưa hợp lệ — hãy đặt lại ghim.')
    expect(wrapper.get('h1').text()).toBe('Đề xuất địa điểm mới')
  })
})

describe('hỗ trợ form đề xuất', () => {
  it('parseTags: tách dấu phẩy, gộp khoảng trắng, bỏ trùng không phân biệt hoa thường', () => {
    expect(parseTags(' View đẹp,  hợp   gia đình ,view ĐẸP,, ')).toEqual([
      'View đẹp',
      'hợp gia đình',
    ])
    expect(parseTags('')).toEqual([])
  })

  it('toOpeningHours: chỉ ngày mở cửa, đánh số 1 = Thứ Hai', () => {
    const week = defaultWeek()
    week[0]!.open = true
    week[6] = { open: true, openTime: '18:00', closeTime: '02:00' }
    expect(toOpeningHours(week)).toEqual([
      { dayOfWeek: 1, openTime: '07:00', closeTime: '22:00' },
      { dayOfWeek: 7, openTime: '18:00', closeTime: '02:00' },
    ])
  })
})
