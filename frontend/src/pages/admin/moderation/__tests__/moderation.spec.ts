import { describe, expect, it } from 'vitest'

import type { Problem } from '@/api/problem'

import { detailRoute, isStale, waitedText } from '../moderation'

function problem(code: string | null): Problem {
  return { status: 409, code, detail: '', fieldErrors: {}, errorId: null, retryAfter: null }
}

describe('moderation helpers', () => {
  it('"Đã chờ" như bản mẫu: phút, giờ, ngày + giờ', () => {
    const now = new Date('2026-10-19T12:00:00Z').getTime()
    expect(waitedText('2026-10-19T11:46:00Z', now)).toBe('14 phút')
    expect(waitedText('2026-10-18T18:00:00Z', now)).toBe('18 giờ')
    expect(waitedText('2026-10-17T11:00:00Z', now)).toBe('2 ngày 1 giờ')
    expect(waitedText('2026-10-17T12:00:00Z', now)).toBe('2 ngày')
  })

  it('báo cáo mở theo đối tượng bị báo cáo, các loại khác theo id', () => {
    expect(detailRoute({ type: 'REPORT', id: 44, reportTargetType: 'COMMENT' })).toEqual({
      name: 'admin-moderation-report',
      params: { targetType: 'COMMENT', targetId: 44 },
    })
    expect(detailRoute({ type: 'CLAIM', id: 3 })).toEqual({
      name: 'admin-moderation-claim',
      params: { id: 3 },
    })
  })

  it('409 "đã xử lý / lệch phiên bản" → tải lại; tự xử lý của mình thì không', () => {
    expect(isStale(problem('REVIEW_ALREADY_MODERATED'))).toBe(true)
    expect(isStale(problem('CONCURRENT_MODIFICATION'))).toBe(true)
    expect(isStale(problem('SELF_ACTION_FORBIDDEN'))).toBe(false)
    expect(isStale(problem(null))).toBe(false)
  })
})
