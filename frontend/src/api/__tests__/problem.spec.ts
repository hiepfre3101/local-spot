import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it } from 'vitest'

import { splitServerErrors } from '@/composables/serverErrors'

import { problemMessage, toProblem } from '../problem'

function axiosError(
  status: number,
  data: unknown,
  headers: Record<string, string> = {},
): AxiosError {
  const config = { headers: {} } as InternalAxiosRequestConfig
  return new AxiosError('x', 'ERR', config, null, { data, status, statusText: '', headers, config })
}

describe('toProblem', () => {
  it('đọc RFC 7807: code, detail, lỗi theo trường (giữ lỗi đầu của mỗi trường), Retry-After', () => {
    const problem = toProblem(
      axiosError(
        429,
        {
          code: 'TOO_MANY_REQUESTS',
          detail: 'Thử lại sau 15 phút.',
          errors: [
            { field: 'email', message: 'a' },
            { field: 'email', message: 'b' },
          ],
        },
        { 'retry-after': '900' },
      ),
    )
    expect(problem).toMatchObject({
      status: 429,
      code: 'TOO_MANY_REQUESTS',
      detail: 'Thử lại sau 15 phút.',
      fieldErrors: { email: 'a' },
      retryAfter: 900,
    })
  })

  it('mất mạng và lỗi 5xx có câu dùng được; 5xx kèm mã tra cứu', () => {
    const network = new AxiosError('Network Error', 'ERR_NETWORK')
    expect(toProblem(network).status).toBe(0)
    expect(problemMessage(toProblem(network))).toContain('Không kết nối được')

    const server = toProblem(axiosError(500, { detail: 'Lỗi hệ thống.', errorId: 'abc-123' }))
    expect(problemMessage(server)).toBe('Lỗi hệ thống. (Mã lỗi: abc-123)')
  })
})

describe('splitServerErrors', () => {
  it('lỗi trường form có → gắn vào trường; trường lạ → gộp vào lỗi chung', () => {
    const problem = toProblem(
      axiosError(422, {
        detail: 'Dữ liệu không hợp lệ.',
        errors: [
          { field: 'email', message: 'Email sai.' },
          { field: 'unknown', message: 'Trường lạ sai.' },
        ],
      }),
    )
    expect(splitServerErrors(problem, ['email'])).toEqual({
      fieldErrors: { email: 'Email sai.' },
      formError: 'Trường lạ sai.',
    })
  })

  it('không có lỗi trường → dùng detail làm lỗi chung', () => {
    const problem = toProblem(axiosError(409, { detail: 'Đã có.' }))
    expect(splitServerErrors(problem, ['email'])).toEqual({ fieldErrors: {}, formError: 'Đã có.' })
  })
})
