import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'

import { catalogApi } from '@/api/catalog'
import type { Amenity, CategoryNode } from '@/types/api'

/** Danh mục / tiện ích gần như không đổi (backend cache 1 giờ) — giữ trong cache trình duyệt cả phiên. */
const CATALOG_STALE_MS = 60 * 60_000

export const catalogKeys = {
  categories: ['catalog', 'categories'] as const,
  amenities: ['catalog', 'amenities'] as const,
  home: ['home'] as const,
}

export function useCategories() {
  const query = useQuery({
    queryKey: catalogKeys.categories,
    queryFn: catalogApi.categories,
    staleTime: CATALOG_STALE_MS,
  })
  /** Tra theo slug (URL dùng slug, API dùng id) — gồm cả danh mục con. */
  const bySlug = computed(() => indexBySlug(query.data.value ?? []))
  return { ...query, bySlug }
}

export function useAmenities() {
  const query = useQuery({
    queryKey: catalogKeys.amenities,
    queryFn: catalogApi.amenities,
    staleTime: CATALOG_STALE_MS,
  })
  const bySlug = computed(
    () => new Map<string, Amenity>((query.data.value ?? []).map((a) => [a.slug, a])),
  )
  return { ...query, bySlug }
}

export function indexBySlug(tree: readonly CategoryNode[]): Map<string, CategoryNode> {
  const map = new Map<string, CategoryNode>()
  for (const root of tree) {
    map.set(root.slug, root)
    for (const child of root.children) map.set(child.slug, child)
  }
  return map
}
