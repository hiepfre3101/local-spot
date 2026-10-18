import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Component } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { Comment, MeResponse, Review } from '@/types/api'

import CommentThread from '../CommentThread.vue'
import ReportDialog from '../ReportDialog.vue'
import ReviewItem from '../ReviewItem.vue'

const api = {
  vote: vi.fn(),
  unvote: vi.fn(),
  comments: vi.fn(),
  addComment: vi.fn(),
  deleteComment: vi.fn(),
  report: vi.fn(),
}
vi.mock('@/api/interactions', () => ({
  interactionsApi: new Proxy(
    {},
    {
      get:
        (_, name: string) =>
        (...args: unknown[]) =>
          api[name as keyof typeof api](...args),
    },
  ),
}))

const notify = { success: vi.fn(), error: vi.fn(), info: vi.fn() }
vi.mock('@/plugins/toast', () => ({ useNotify: () => notify }))

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

function review(overrides: Partial<Review> = {}): Review {
  return {
    id: 7,
    placeId: 5,
    place: { id: 5, slug: 'pho-thin-ha-noi', name: 'Phở Thìn' },
    author: { id: 100, displayName: 'Tuấn Lê', avatarUrl: null },
    rating: 4,
    content: 'Nước dùng trong, thịt bò mềm.',
    visitedAt: '2026-10-01',
    status: 'PUBLISHED',
    rejectReason: null,
    photos: [],
    helpfulCount: 0,
    votedByMe: false,
    authorCheckedIn: false,
    commentCount: 0,
    ownerReply: null,
    createdAt: '2026-10-02T03:00:00Z',
    updatedAt: '2026-10-02T03:00:00Z',
    version: 0,
    ...overrides,
  }
}

function comment(id: number, authorId: number, overrides: Partial<Comment> = {}): Comment {
  return {
    id,
    author: { id: authorId, displayName: `Người ${authorId}`, avatarUrl: null },
    content: `Bình luận ${id}`,
    parentId: null,
    deleted: false,
    replies: [],
    createdAt: '2026-10-03T03:00:00Z',
    ...overrides,
  }
}

function httpError(status: number, data: object, headers: Record<string, string> = {}): AxiosError {
  const config = { headers: {} } as InternalAxiosRequestConfig
  return new AxiosError('x', 'ERR', config, null, {
    data,
    status,
    statusText: '',
    headers,
    config,
  })
}

let router: Router

async function mountWith(
  component: Component,
  props: Record<string, unknown>,
): Promise<VueWrapper> {
  const Empty = { render: () => null }
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Empty },
      { path: '/login', name: 'login', component: Empty },
      { path: '/places/:slug', name: 'place', component: Empty },
      { path: '/places/:slug/review', name: 'write-review', component: Empty },
    ],
  })
  await router.push('/places/pho-thin')
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  // Ba component khác nhau dùng chung khung mount — props kiểm ở từng test, không ở đây
  const wrapper = mount(
    component as Component,
    {
      props,
      global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
    } as never,
  ) as VueWrapper
  await flushPromises()
  return wrapper
}

/** Gõ vào ô có `placeholder` rồi gửi đúng form chứa nó. */
async function submitTextarea(wrapper: VueWrapper, placeholder: string, text: string) {
  const textarea = wrapper.get(`textarea[placeholder="${placeholder}"]`)
  await textarea.setValue(text)
  await wrapper
    .findAll('form')
    .find((f) => f.element.contains(textarea.element))!
    .trigger('submit')
  await flushPromises()
}

const buttonByText = (wrapper: VueWrapper, text: string) =>
  wrapper.findAll('button').find((b) => b.text().startsWith(text))

beforeEach(() => {
  setActivePinia(createPinia())
  Object.values(api).forEach((fn) => fn.mockReset())
  Object.values(notify).forEach((fn) => fn.mockReset())
})

describe('ReviewItem — vote, báo cáo, của chính mình', () => {
  it('vote / bỏ vote theo kết quả backend; không hiện số 0', async () => {
    useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
    api.vote.mockResolvedValue({ helpfulCount: 1, votedByMe: true })
    api.unvote.mockResolvedValue({ helpfulCount: 0, votedByMe: false })
    const wrapper = await mountWith(ReviewItem, { review: review(), placeSlug: 'pho-thin' })

    const helpful = buttonByText(wrapper, 'Hữu ích')!
    expect(helpful.text()).toBe('Hữu ích')
    expect(helpful.attributes('aria-pressed')).toBe('false')
    await helpful.trigger('click')
    await flushPromises()
    expect(api.vote).toHaveBeenCalledWith(7)
    expect(helpful.text()).toBe('Hữu ích · 1')
    expect(helpful.attributes('aria-pressed')).toBe('true')

    await helpful.trigger('click')
    await flushPromises()
    expect(api.unvote).toHaveBeenCalledWith(7)
    expect(helpful.attributes('aria-pressed')).toBe('false')
  })

  it('khách bấm Hữu ích / Báo cáo → sang đăng nhập, quay lại đúng trang', async () => {
    const wrapper = await mountWith(ReviewItem, { review: review(), placeSlug: 'pho-thin' })
    await buttonByText(wrapper, 'Báo cáo')!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/places/pho-thin')
    expect(api.vote).not.toHaveBeenCalled()
    expect(wrapper.emitted('report')).toBeUndefined()
  })

  it('báo cáo đánh giá của người khác → báo trang cha mở hộp thoại', async () => {
    useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
    const wrapper = await mountWith(ReviewItem, { review: review(), placeSlug: 'pho-thin' })
    await buttonByText(wrapper, 'Báo cáo')!.trigger('click')
    expect(wrapper.emitted('report')![0]).toEqual([
      { type: 'REVIEW', id: 7, label: 'đánh giá của Tuấn Lê' },
    ])
  })

  it('đánh giá của mình: Sửa thay cho Hữu ích / Báo cáo; bản chờ duyệt có nhãn và không có bình luận', async () => {
    useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
    const own = review({ author: { id: 42, displayName: 'Minh Anh', avatarUrl: null } })
    let wrapper = await mountWith(ReviewItem, { review: own, placeSlug: 'pho-thin' })
    expect(buttonByText(wrapper, 'Hữu ích')).toBeUndefined()
    expect(buttonByText(wrapper, 'Báo cáo')).toBeUndefined()
    expect(wrapper.get('a.ls-act').text()).toBe('Sửa')
    expect(buttonByText(wrapper, 'Bình luận')).toBeDefined()
    wrapper.unmount()

    wrapper = await mountWith(ReviewItem, {
      review: { ...own, status: 'PENDING' },
      placeSlug: 'pho-thin',
    })
    expect(wrapper.text()).toContain('● Đang chờ duyệt — chỉ bạn thấy')
    expect(buttonByText(wrapper, 'Bình luận')).toBeUndefined()
    expect(wrapper.get('a.ls-act').text()).toBe('Sửa')
  })
})

describe('CommentThread — 2 cấp, trả lời, xoá, giới hạn', () => {
  beforeEach(() => {
    useAuthStore().$patch({ user: { ...ME }, accessToken: 'token' })
  })

  it('gốc kèm trả lời, gốc đã xoá thành dòng giữ chỗ; chỉ gốc có nút Trả lời', async () => {
    api.comments.mockResolvedValue({
      items: [
        comment(1, 100, { replies: [comment(2, 42, { parentId: 1, replies: null })] }),
        comment(3, 101, {
          deleted: true,
          author: null,
          content: null,
          replies: [comment(4, 101, { parentId: 3, replies: null })],
        }),
      ],
      nextCursor: null,
    })
    const wrapper = await mountWith(CommentThread, { reviewId: 7 })
    expect(wrapper.text()).toContain('Bình luận 1')
    expect(wrapper.text()).toContain('Bình luận 2')
    expect(wrapper.text()).toContain('Bình luận đã bị xoá.')
    expect(wrapper.findAll('button').filter((b) => b.text() === 'Trả lời')).toHaveLength(1)
    // Bình luận 2 của tôi → Xoá; của người khác → Báo cáo
    expect(wrapper.findAll('button').filter((b) => b.text() === 'Xoá')).toHaveLength(1)
    expect(wrapper.findAll('button').filter((b) => b.text() === 'Báo cáo')).toHaveLength(2)
  })

  it('gửi bình luận gốc và trả lời; báo tăng số bình luận', async () => {
    api.comments.mockResolvedValue({ items: [comment(1, 100)], nextCursor: null })
    api.addComment.mockResolvedValue(comment(9, 42))
    const wrapper = await mountWith(CommentThread, { reviewId: 7 })

    await submitTextarea(wrapper, 'Viết bình luận…', '  Đồng ý, quán rất ngon.  ')
    expect(api.addComment).toHaveBeenCalledWith(7, 'Đồng ý, quán rất ngon.', null)
    expect(wrapper.emitted('countChange')![0]).toEqual([1])

    await buttonByText(wrapper, 'Trả lời')!.trigger('click')
    await submitTextarea(wrapper, 'Viết câu trả lời…', 'Cảm ơn bạn!')
    expect(api.addComment).toHaveBeenLastCalledWith(7, 'Cảm ơn bạn!', 1)
  })

  it('vượt giới hạn (429) → nói rõ thời gian chờ', async () => {
    api.comments.mockResolvedValue({ items: [], nextCursor: null })
    api.addComment.mockRejectedValue(
      httpError(429, { code: 'TOO_MANY_REQUESTS', detail: 'x' }, { 'retry-after': '240' }),
    )
    const wrapper = await mountWith(CommentThread, { reviewId: 7 })
    await wrapper.get('textarea').setValue('Bình luận nữa.')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('thử lại sau 4 phút')
  })

  it('xoá bình luận của mình cần xác nhận', async () => {
    api.comments.mockResolvedValue({ items: [comment(5, 42)], nextCursor: null })
    api.deleteComment.mockResolvedValue(undefined)
    const wrapper = await mountWith(CommentThread, { reviewId: 7 })
    await buttonByText(wrapper, 'Xoá')!.trigger('click')
    expect(api.deleteComment).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Xoá bình luận này?')
    await wrapper
      .findAll('button')
      .filter((b) => b.text() === 'Xoá')[0]!
      .trigger('click')
    await flushPromises()
    expect(api.deleteComment).toHaveBeenCalledWith(5)
    expect(wrapper.emitted('countChange')![0]).toEqual([-1])
  })
})

describe('ReportDialog', () => {
  beforeEach(() => {
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true
    }
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false
    }
  })

  async function openDialog() {
    const wrapper = await mountWith(ReportDialog, {})
    ;(wrapper.vm as unknown as { open: (t: object) => void }).open({
      type: 'PLACE',
      id: 5,
      label: 'Phở Thìn',
    })
    await flushPromises()
    return wrapper
  }

  it('"Thông tin sai" bắt buộc mô tả; gửi đúng dữ liệu', async () => {
    api.report.mockResolvedValue(undefined)
    const wrapper = await openDialog()
    expect(wrapper.text()).toContain('Báo cáo Phở Thìn')

    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain('Chọn một lý do.')

    await wrapper.get('input[value="WRONG_INFO"]').setValue(true)
    await wrapper.get('form').trigger('submit')
    expect(api.report).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('Mô tả cụ thể')

    await wrapper.get('textarea').setValue('  Quán đã chuyển sang số 20.  ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.report).toHaveBeenCalledWith('PLACE', 5, 'WRONG_INFO', 'Quán đã chuyển sang số 20.')
    expect(notify.success).toHaveBeenCalled()
  })

  it('đã báo cáo rồi (409) → thông báo nhẹ, không coi là lỗi', async () => {
    api.report.mockRejectedValue(httpError(409, { code: 'REPORT_ALREADY_EXISTS', detail: 'x' }))
    const wrapper = await openDialog()
    await wrapper.get('input[value="SPAM"]').setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(notify.info).toHaveBeenCalledWith(expect.stringContaining('đã báo cáo'))
    expect(notify.error).not.toHaveBeenCalled()
  })
})
