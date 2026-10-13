import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'

import { placesApi, type PlaceFilter, type PlaceSort } from '@/api/places'
import type { GeoPoint, PlaceSummary } from '@/types/api'
import { effectiveSort, searchMode, type SearchState } from '@/utils/searchQuery'

/** Số địa điểm tối đa của `/places/nearby` (openapi Limit ≤ 50) — không phân trang. */
export const NEARBY_LIMIT = 50
const PAGE_SIZE = 20

export interface SearchInputs {
  state: SearchState
  /** Id đã tra từ slug — `undefined` khi URL không lọc; `null` khi danh mục / tiện ích chưa tải xong. */
  categoryId: number | undefined | null
  amenityIds: number[] | null
  /** Tọa độ thật của `near` (vị trí người dùng đã hỏi được, hoặc tâm vùng trên bản đồ). */
  center: GeoPoint | null
}

/**
 * Kết quả P02 theo chế độ: duyệt `/places` và tìm `/search` phân trang cursor (infinite scroll); quanh vị trí
 * `/places/nearby` một lần ≤ 50. Giữ kết quả cũ trong lúc tải bộ lọc mới (`keepPreviousData`) để danh sách / bản đồ
 * không nháy trắng.
 */
export function useSearchResults(inputs: Ref<SearchInputs>) {
  const mode = computed(() => searchMode(inputs.value.state))
  const ready = computed(() => inputs.value.categoryId !== null && inputs.value.amenityIds !== null)

  const filter = computed<PlaceFilter>(() => ({
    categoryId: inputs.value.categoryId ?? undefined,
    amenityIds: inputs.value.amenityIds ?? undefined,
    priceMax: inputs.value.state.price ?? undefined,
    minRating: inputs.value.state.minRating ?? undefined,
  }))
  const sort = computed(() => effectiveSort(inputs.value.state))

  const paged = useInfiniteQuery({
    queryKey: computed(() => [
      'places',
      mode.value,
      inputs.value.state.q,
      filter.value,
      sort.value,
    ]),
    queryFn: ({ pageParam }) =>
      mode.value === 'search'
        ? placesApi.search(
            inputs.value.state.q,
            filter.value,
            sort.value ?? 'RELEVANCE',
            pageParam,
            PAGE_SIZE,
          )
        : placesApi
            .list(filter.value, (sort.value ?? 'SCORE') as PlaceSort, pageParam, PAGE_SIZE)
            .then((page) => ({ ...page, degraded: false })),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    enabled: computed(() => ready.value && mode.value !== 'nearby'),
    placeholderData: keepPreviousData,
  })

  const nearby = useQuery({
    queryKey: computed(() => [
      'places',
      'nearby',
      inputs.value.center,
      inputs.value.state.radius,
      inputs.value.categoryId ?? null,
    ]),
    queryFn: () =>
      placesApi.nearby({
        lat: inputs.value.center!.lat,
        lng: inputs.value.center!.lng,
        radius: inputs.value.state.radius,
        categoryId: inputs.value.categoryId ?? undefined,
        limit: NEARBY_LIMIT,
      }),
    enabled: computed(() => ready.value && mode.value === 'nearby' && inputs.value.center !== null),
    placeholderData: keepPreviousData,
  })

  const items = computed<PlaceSummary[]>(() =>
    mode.value === 'nearby'
      ? (nearby.data.value ?? [])
      : (paged.data.value?.pages.flatMap((p) => p.items) ?? []),
  )
  const active = computed(() => (mode.value === 'nearby' ? nearby : paged))

  return {
    mode,
    items,
    degraded: computed(
      () => mode.value === 'search' && (paged.data.value?.pages.some((p) => p.degraded) ?? false),
    ),
    isPending: computed(() => !ready.value || active.value.isPending.value),
    isFetching: computed(() => active.value.isFetching.value),
    isError: computed(() => active.value.isError.value),
    error: computed(() => active.value.error.value),
    refetch: () => active.value.refetch(),
    hasNextPage: computed(() => mode.value !== 'nearby' && paged.hasNextPage.value),
    isFetchingNextPage: computed(() => paged.isFetchingNextPage.value),
    fetchNextPage: () => paged.fetchNextPage(),
    /** Đủ 50 = có thể còn địa điểm xa hơn trong bán kính (API không trả tổng). */
    nearbyCapped: computed(
      () => mode.value === 'nearby' && (nearby.data.value?.length ?? 0) >= NEARBY_LIMIT,
    ),
  }
}
