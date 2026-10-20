import { QueryClient } from '@tanstack/vue-query'
import { isAxiosError } from 'axios'

/**
 * TanStack Query: cache dữ liệu đọc từ API. Chỉ thử lại lỗi mạng / 5xx (tối đa 1 lần) — lỗi 4xx là kết quả xác định
 * (sai quyền, không tồn tại), thử lại chỉ chậm thêm.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          const status = isAxiosError(error) ? error.response?.status : undefined
          return failureCount < 1 && (status === undefined || status >= 500)
        },
      },
    },
  })
}
