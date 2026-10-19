import { useQueryClient } from '@tanstack/vue-query'
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { moderationApi } from '@/api/moderation'
import { problemMessage, toProblem } from '@/api/problem'
import { useNotify } from '@/plugins/toast'
import type { ModerationItemType } from '@/types/api'

import { detailRoute, isStale } from './moderation'

/**
 * Gửi một quyết định kiểm duyệt rồi **sang việc kế tiếp cùng loại** (FIFO — DataTable: "Xử lý tiếp"); hết việc thì về
 * hàng chờ. Mục vừa bị người khác xử lý / đổi phiên bản (409) → báo và tải lại để thấy kết quả thật, không đoán.
 */
export function useDecision(type: ModerationItemType, reload: () => unknown) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const notify = useNotify()
  const submitting = ref(false)
  const error = ref<string | null>(null)

  async function submit(send: () => Promise<void>, doneText: string): Promise<boolean> {
    submitting.value = true
    error.value = null
    try {
      await send()
    } catch (e) {
      const problem = toProblem(e)
      error.value = problemMessage(problem)
      if (isStale(problem)) {
        void reload()
      }
      return false
    } finally {
      submitting.value = false
    }
    notify.success(`✓ ${doneText}`)
    void queryClient.invalidateQueries({ queryKey: ['moderation'] })
    await goToNext()
    return true
  }

  async function goToNext(): Promise<void> {
    try {
      const page = await moderationApi.queue(type, null, 1)
      const next = page.items[0]
      if (next) {
        await router.replace(detailRoute(next))
        return
      }
    } catch {
      // không đọc được hàng chờ — về danh sách là an toàn
    }
    notify.info('● Đã hết việc loại này trong hàng chờ.')
    await router.push({ name: 'admin-queue', query: { type } })
  }

  return { submitting, error, submit }
}
