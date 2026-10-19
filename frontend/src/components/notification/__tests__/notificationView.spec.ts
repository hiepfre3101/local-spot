import { describe, expect, it } from 'vitest'

import type { AppNotification, NotificationData, NotificationType } from '@/types/api'

import { describeNotification } from '../notificationView'

const place = { placeId: 3, placeName: 'Cà phê Mộc', placeSlug: 'ca-phe-moc' }

function n(type: NotificationType, data: NotificationData): AppNotification {
  return { id: 1, type, data, readAt: null, createdAt: '2026-10-19T03:00:00Z' }
}

function sentence(notification: AppNotification): string {
  return describeNotification(notification, 42)
    .parts.map((p) => p.text)
    .join('')
}

describe('describeNotification', () => {
  it('mọi loại đều có câu và tên địa điểm in đậm khi có', () => {
    const types: NotificationType[] = [
      'PLACE_APPROVED',
      'PLACE_REJECTED',
      'REVIEW_PUBLISHED',
      'REVIEW_REJECTED',
      'NEW_REVIEW_ON_MY_PLACE',
      'OWNER_REPLIED',
      'CLAIM_SUBMITTED',
      'CLAIM_APPROVED',
      'CLAIM_REJECTED',
    ]
    for (const type of types) {
      const view = describeNotification(n(type, place), 42)
      expect(view.parts.some((p) => p.strong && p.text === 'Cà phê Mộc')).toBe(true)
    }
  })

  it('đích theo người nhận: trang địa điểm, quản lý địa điểm, đề xuất của tôi', () => {
    expect(describeNotification(n('PLACE_APPROVED', place), 42).to).toEqual({
      name: 'place',
      params: { slug: 'ca-phe-moc' },
    })
    expect(describeNotification(n('NEW_REVIEW_ON_MY_PLACE', place), 42).to).toEqual({
      name: 'owner-place',
      params: { id: 3 },
      query: { tab: 'reviews' },
    })
    expect(describeNotification(n('PLACE_REJECTED', place), 42).to).toEqual({
      name: 'profile',
      params: { id: 42 },
      query: { tab: 'places' },
    })
    expect(describeNotification(n('CLAIM_REJECTED', place), 42).to).toEqual({ name: 'owner' })
  })

  it('từ chối kèm lý do ở dòng phụ', () => {
    const view = describeNotification(
      n('REVIEW_REJECTED', { ...place, reason: 'Nội dung quảng cáo' }),
      42,
    )
    expect(view.detail).toBe('Lý do: Nội dung quảng cáo')
    expect(view.icon).toBe('cross')
  })

  it('đánh giá mới cho chủ: tên người viết + điểm', () => {
    expect(
      sentence(n('NEW_REVIEW_ON_MY_PLACE', { ...place, authorName: 'Tuấn Lê', rating: 4 })),
    ).toBe('Tuấn Lê vừa đánh giá Cà phê Mộc · 4/5')
  })

  it('báo cáo: không có liên kết (nội dung đã ẩn), lý do theo nhãn + ghi nhận vi phạm', () => {
    const view = describeNotification(
      n('REPORT_ACTION_TAKEN', {
        targetType: 'REVIEW',
        targetId: 9,
        preview: 'Quán dở tệ',
        confirmedReason: 'SPAM',
        violation: true,
      }),
      42,
    )
    expect(view.to).toBeNull()
    expect(view.parts.map((p) => p.text).join('')).toBe(
      'Đánh giá của bạn “Quán dở tệ” đã bị ẩn sau khi xét báo cáo',
    )
    expect(view.detail).toBe('Lý do: Spam hoặc quảng cáo · Ghi nhận một vi phạm')

    expect(
      sentence(n('REPORT_RESOLVED', { targetType: 'COMMENT', targetId: 2, preview: 'abc' })),
    ).toBe('Báo cáo của bạn về một bình luận “abc” đã được xử lý')
  })
})
