import { readonly, ref } from 'vue'

export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'localspot-theme'
const theme = ref<Theme>('light')

/**
 * Theme Sáng / Tối của design system (`<html data-theme>`). Mặc định theo hệ điều hành; người dùng chọn thì nhớ trong
 * localStorage (chỉ là tuỳ chọn hiển thị của trình duyệt này).
 */
export function initTheme(): void {
  let stored: string | null = null
  try {
    stored = localStorage.getItem(STORAGE_KEY)
  } catch {
    // trình duyệt chặn storage — dùng theo hệ điều hành
  }
  const prefersDark =
    typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches
  apply(stored === 'light' || stored === 'dark' ? stored : prefersDark ? 'dark' : 'light')
}

export function useTheme() {
  function toggle(): void {
    const next: Theme = theme.value === 'dark' ? 'light' : 'dark'
    apply(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // bỏ qua — vẫn đổi được trong phiên này
    }
  }
  return { theme: readonly(theme), toggle }
}

function apply(value: Theme): void {
  theme.value = value
  document.documentElement.dataset.theme = value
}
