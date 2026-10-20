import { z } from 'zod'

/**
 * Luật validate tài khoản — đồng bộ với backend (`RegisterRequest`, `@ValidPassword`, U1) để báo lỗi ngay trên form;
 * backend vẫn kiểm lại và lỗi 422 của nó được gắn vào đúng trường.
 */
export const emailSchema = z
  .string({ required_error: 'Nhập email.' })
  .trim()
  .min(1, 'Nhập email.')
  .max(191, 'Email tối đa 191 ký tự.')
  .email('Email chưa đúng định dạng.')

/** U1: ≥ 8 ký tự, có chữ và số; tối đa 72 byte do giới hạn BCrypt (chữ có dấu chiếm 2–3 byte). */
export const newPasswordSchema = z
  .string({ required_error: 'Nhập mật khẩu.' })
  .min(8, 'Mật khẩu cần ít nhất 8 ký tự.')
  .regex(/[A-Za-z]/, 'Mật khẩu cần có chữ cái.')
  .regex(/\d/, 'Mật khẩu cần có chữ số.')
  .refine((v) => new TextEncoder().encode(v).length <= 72, 'Mật khẩu quá dài (tối đa 72 byte).')

export const displayNameSchema = z
  .string({ required_error: 'Nhập tên hiển thị.' })
  .trim()
  .min(2, 'Tên hiển thị cần ít nhất 2 ký tự.')
  .max(100, 'Tên hiển thị tối đa 100 ký tự.')

/** Hai ô mật khẩu mới phải khớp — lỗi gắn vào ô nhập lại. */
export function withConfirmation<T extends z.ZodRawShape>(shape: T) {
  return z
    .object({ ...shape, newPassword: newPasswordSchema, confirmPassword: z.string() })
    .refine((v) => v.newPassword === v.confirmPassword, {
      message: 'Hai mật khẩu chưa khớp.',
      path: ['confirmPassword'],
    })
}
