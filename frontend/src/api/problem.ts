import { isAxiosError } from 'axios'

import type { ProblemBody } from '@/types/api'

/**
 * Lỗi API đã chuẩn hoá từ RFC 7807 (`GlobalExceptionHandler`). Frontend rẽ nhánh theo `code` (ổn định), không theo câu
 * chữ; `detail` là câu tiếng Việt hiển thị được do backend soạn.
 */
export interface Problem {
  /** 0 = không nhận được phản hồi (mất mạng, máy chủ không chạy). */
  status: number
  code: string | null
  detail: string
  fieldErrors: Record<string, string>
  /** Chỉ có ở lỗi 5xx — người dùng gửi mã này khi báo lỗi. */
  errorId: string | null
  /** Giây, từ header `Retry-After` của 429. */
  retryAfter: number | null
}

const NETWORK_MESSAGE = 'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.'
const FALLBACK_MESSAGE = 'Đã có lỗi xảy ra. Hãy thử lại sau ít phút.'

export function toProblem(error: unknown): Problem {
  if (!isAxiosError(error)) {
    return emptyProblem(0, FALLBACK_MESSAGE)
  }
  const response = error.response
  if (!response) {
    return emptyProblem(0, NETWORK_MESSAGE)
  }
  const body = (isObject(response.data) ? response.data : {}) as ProblemBody
  const fieldErrors: Record<string, string> = {}
  for (const e of body.errors ?? []) {
    // Giữ lỗi đầu tiên của mỗi trường — form chỉ hiện một dòng dưới ô nhập
    fieldErrors[e.field] ??= e.message
  }
  const retryAfterHeader = Number(response.headers?.['retry-after'])
  return {
    status: response.status,
    code: body.code ?? null,
    detail: body.detail || FALLBACK_MESSAGE,
    fieldErrors,
    errorId: body.errorId ?? null,
    retryAfter: Number.isFinite(retryAfterHeader) && retryAfterHeader > 0 ? retryAfterHeader : null,
  }
}

/** Câu báo lỗi cho người dùng; lỗi 5xx kèm mã tra cứu để báo lại cho quản trị. */
export function problemMessage(problem: Problem): string {
  if (problem.errorId) {
    return `${problem.detail} (Mã lỗi: ${problem.errorId})`
  }
  return problem.detail
}

function emptyProblem(status: number, detail: string): Problem {
  return { status, code: null, detail, fieldErrors: {}, errorId: null, retryAfter: null }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
