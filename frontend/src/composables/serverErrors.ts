import { problemMessage, type Problem } from '@/api/problem'

/**
 * Tách lỗi 422 của backend thành lỗi theo trường (gắn dưới ô nhập) và lỗi chung của form. Trường backend báo mà form
 * không có (lệch tên) thì gộp vào lỗi chung — không để lỗi biến mất.
 */
export function splitServerErrors(
  problem: Problem,
  formFields: readonly string[],
): { fieldErrors: Record<string, string>; formError: string | null } {
  const fieldErrors: Record<string, string> = {}
  const unmatched: string[] = []
  for (const [field, message] of Object.entries(problem.fieldErrors)) {
    if (formFields.includes(field)) {
      fieldErrors[field] = message
    } else {
      unmatched.push(message)
    }
  }
  const hasFieldErrors = Object.keys(fieldErrors).length > 0
  const formError =
    unmatched.length > 0 ? unmatched.join(' ') : hasFieldErrors ? null : problemMessage(problem)
  return { fieldErrors, formError }
}
