import Toast, { POSITION, useToast, type PluginOptions } from 'vue-toastification'

/** Cấu hình vue-toastification; kiểu dáng ở assets/toast.css (theo design system). */
export const toastOptions: PluginOptions = {
  position: POSITION.BOTTOM_RIGHT,
  timeout: 5000,
  hideProgressBar: true,
  icon: false,
  maxToasts: 4,
}

export { Toast }

/**
 * Thông báo nổi. Trạng thái luôn có ký hiệu + chữ, không chỉ bằng màu (README design system): ✓ thành công, ✕ lỗi,
 * ● thông tin.
 */
export function useNotify() {
  const toast = useToast()
  return {
    success: (message: string) => toast.success(`✓ ${message}`),
    error: (message: string) => toast.error(`✕ ${message}`),
    info: (message: string) => toast.info(`● ${message}`),
  }
}
