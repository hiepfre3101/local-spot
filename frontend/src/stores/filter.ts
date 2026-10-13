import { defineStore } from 'pinia'
import { computed, shallowRef } from 'vue'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'

import {
  emptySearchState,
  parseSearchQuery,
  toSearchQuery,
  type SearchState,
} from '@/utils/searchQuery'

/**
 * Bộ lọc tìm kiếm (plan §2 — store `filter`). **URL là nguồn sự thật** (sitemap §2.1): store không giữ bản sao bộ lọc
 * đang áp dụng mà đọc từ query của route `/search` và ghi lại bằng router — liên kết chia sẻ được, nút Quay lại đúng.
 * Store chỉ nhớ thêm lần tìm gần nhất để trang chi tiết dựng liên kết "← Kết quả tìm kiếm".
 */
export const useFilterStore = defineStore('filter', () => {
  const router = useRouter()
  const route = useRoute()
  /** Query của lần tìm gần nhất (để quay lại đúng bộ lọc từ trang chi tiết). */
  const lastQuery = shallowRef<LocationQueryRaw | null>(null)

  const state = computed<SearchState>(() =>
    route.name === 'search' ? parseSearchQuery(route.query) : emptySearchState(),
  )

  /**
   * Đổi một phần bộ lọc. `push` cho thay đổi người dùng muốn Quay lại được (từ khóa mới, bật "Quanh tôi"); chip lọc
   * dùng `replace` để lịch sử không đầy các bước bật / tắt.
   */
  function update(patch: Partial<SearchState>, mode: 'push' | 'replace' = 'replace') {
    const query = toSearchQuery({ ...state.value, ...patch })
    lastQuery.value = query
    return router[mode]({ name: 'search', query })
  }

  /** Mở trang tìm kiếm với bộ lọc mới hoàn toàn (ô tìm trang chủ, danh mục, "Tìm quanh tôi"). */
  function open(patch: Partial<SearchState>) {
    const query = toSearchQuery({ ...emptySearchState(), ...patch })
    lastQuery.value = query
    return router.push({ name: 'search', query })
  }

  function remember(query: LocationQueryRaw) {
    lastQuery.value = query
  }

  return { state, lastQuery, update, open, remember }
})
