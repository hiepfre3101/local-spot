import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

/**
 * Khách vãng lai thấy đủ nội dung; bấm thao tác cần tài khoản → mở đăng nhập rồi quay lại đúng trang (ReviewItem README,
 * UC03). Trả `true` khi đã đăng nhập — gọi ở đầu mỗi thao tác: `if (!requireLogin()) return`.
 */
export function useRequireLogin(): () => boolean {
  const auth = useAuthStore()
  const route = useRoute()
  const router = useRouter()
  return () => {
    if (auth.isAuthenticated) return true
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return false
  }
}
