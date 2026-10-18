import type { QueryClient } from '@tanstack/vue-query'

/**
 * Khoá cache dùng chung của module bộ sưu tập — nút "Lưu" trên P03 và hộp chọn bộ đọc cùng một truy vấn
 * `/me/collections?placeId=`, nên tick trong hộp thì nút đổi trạng thái ngay.
 */
export const collectionKeys = {
  forPlace: (placeId: number) => ['me', 'collections', { placeId }] as const,
  detail: (collectionId: number) => ['collection', collectionId] as const,
  shared: (shareSlug: string) => ['collection', 'shared', shareSlug] as const,
}

/** Sau mỗi thay đổi: danh sách của tôi (mọi biến thể), chi tiết, tab Bộ sưu tập trên trang cá nhân. */
export function invalidateCollections(queryClient: QueryClient, ownerId?: number): void {
  void queryClient.invalidateQueries({ queryKey: ['me', 'collections'] })
  void queryClient.invalidateQueries({ queryKey: ['collection'] })
  if (ownerId !== undefined) {
    void queryClient.invalidateQueries({ queryKey: ['user', ownerId, 'collections'] })
  }
}
