import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import BannerPicture from '@/components/ui/BannerPicture.vue'
import type { Banner, HomeBanner } from '@/types/api'

import BannerSlotPanel from '../BannerSlotPanel.vue'

const update = vi.fn()
const remove = vi.fn()

vi.mock('@/api/banners', () => ({
  bannersApi: {
    update: (...args: unknown[]) => update(...args),
    remove: (...args: unknown[]) => remove(...args),
    replaceImage: vi.fn(),
  },
}))

const IMAGE = {
  id: 3,
  status: 'READY' as const,
  thumbUrl: 'http://cdn.test/banners/a-thumb.jpg',
  mediumUrl: 'http://cdn.test/banners/a-medium.jpg',
  largeUrl: 'http://cdn.test/banners/a-large.jpg',
}

const WITH_IMAGE: Banner = {
  slot: 'HERO',
  id: 3,
  image: IMAGE,
  altText: 'Phố cổ',
  active: true,
  version: 2,
  updatedAt: '2026-10-17T07:00:00Z',
  updatedBy: { id: 1, displayName: 'Lan Phạm', avatarUrl: null },
}

const EMPTY: Banner = {
  slot: 'COMMUNITY',
  id: null,
  image: null,
  altText: '',
  active: false,
  version: null,
  updatedAt: null,
  updatedBy: null,
}

function mountPanel(banner: Banner) {
  return mount(BannerSlotPanel, {
    props: {
      banner,
      title: 'Nền hero',
      description: 'Ảnh nền',
      crop: { label: 'Khung hero', aspect: 15 / 7 },
    },
  })
}

function button(wrapper: ReturnType<typeof mountPanel>, text: string) {
  return wrapper.findAll('button').find((b) => b.text() === text)
}

describe('BannerSlotPanel (UC34)', () => {
  beforeEach(() => {
    update.mockReset()
    remove.mockReset()
  })

  it('ô chưa có ảnh: hình minh hoạ, trạng thái bằng chữ, chỉ có nút tải ảnh', () => {
    const wrapper = mountPanel(EMPTY)
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('○ Chưa có ảnh — đang dùng hình minh hoạ')
    expect(wrapper.text()).toContain('Tải ảnh lên')
    expect(button(wrapper, 'Tắt ảnh')).toBeUndefined()
    expect(button(wrapper, 'Gỡ ảnh')).toBeUndefined()
  })

  it('tắt ảnh gửi version, báo lên trang cha ô mới nhất', async () => {
    update.mockResolvedValue({ ...WITH_IMAGE, active: false, version: 3 })
    const wrapper = mountPanel(WITH_IMAGE)
    expect(wrapper.text()).toContain('✓ Đang hiện trên trang chủ')
    expect(wrapper.text()).toContain('Cập nhật bởi Lan Phạm')

    await button(wrapper, 'Tắt ảnh')!.trigger('click')
    await flushPromises()
    expect(update).toHaveBeenCalledWith('HERO', { active: false, version: 2 })
    expect(wrapper.emitted('changed')![0]).toEqual([{ ...WITH_IMAGE, active: false, version: 3 }])
  })

  it('lưu mô tả chỉ bật khi đã sửa; lỗi 409 hiện thành câu báo lỗi', async () => {
    const wrapper = mountPanel(WITH_IMAGE)
    expect(button(wrapper, 'Lưu mô tả')!.attributes('disabled')).toBeDefined()
    await wrapper.get('input:not([type="file"])').setValue('  Phố cổ Hội An lên đèn ')
    expect(button(wrapper, 'Lưu mô tả')!.attributes('disabled')).toBeUndefined()

    update.mockRejectedValue(new Error('network'))
    await button(wrapper, 'Lưu mô tả')!.trigger('click')
    await flushPromises()
    expect(update).toHaveBeenCalledWith('HERO', { altText: 'Phố cổ Hội An lên đèn', version: 2 })
    expect(wrapper.get('[role="alert"]').text()).toContain('✕')
  })

  it('gỡ ảnh cần xác nhận, xong ô về trạng thái trống', async () => {
    remove.mockResolvedValue(undefined)
    const wrapper = mountPanel(WITH_IMAGE)
    await button(wrapper, 'Gỡ ảnh')!.trigger('click')
    expect(remove).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Gỡ ảnh này?')

    await button(wrapper, 'Gỡ ảnh')!.trigger('click') // nút xác nhận
    await flushPromises()
    expect(remove).toHaveBeenCalledWith('HERO')
    const [emitted] = wrapper.emitted('changed')![0] as [Banner]
    expect(emitted).toMatchObject({ slot: 'HERO', image: null, active: false, version: null })
  })
})

describe('BannerPicture (trang chủ)', () => {
  const banner: HomeBanner = { slot: 'HERO', image: IMAGE, altText: '' }

  it('có ảnh: srcset 3 cỡ, alt rỗng cho ảnh trang trí, lớp phủ tối khi có chữ đè lên', () => {
    const wrapper = mount(BannerPicture, { props: { banner, scrim: true } })
    const img = wrapper.get('img')
    expect(img.attributes('srcset')).toContain('a-large.jpg 1920w')
    expect(img.attributes('alt')).toBe('')
    expect(wrapper.find('.banner-pic__scrim').exists()).toBe(true)
  })

  it('không có ảnh: hình minh hoạ phẳng như trước', () => {
    const wrapper = mount(BannerPicture, { props: { banner: null } })
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('svg').exists()).toBe(true)
  })
})
