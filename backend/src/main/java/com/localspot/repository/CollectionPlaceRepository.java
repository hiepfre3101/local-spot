package com.localspot.repository;

import com.localspot.entity.CollectionPlace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionPlaceRepository extends JpaRepository<CollectionPlace, CollectionPlace.Id> {

    /** Mọi dòng, kể cả địa điểm đã bị ẩn — trần 100 đếm theo dòng, không theo địa điểm đang hiện. */
    long countByCollectionId(Long collectionId);

    /** Chi tiết: thêm gần nhất trước, nạp sẵn địa điểm + danh mục cho thẻ (≤ 100 dòng). */
    @Query("""
            SELECT cp FROM CollectionPlace cp JOIN FETCH cp.place p JOIN FETCH p.category
            WHERE cp.collection.id = :collectionId
            ORDER BY cp.addedAt DESC, p.id DESC
            """)
    List<CollectionPlace> findItems(@Param("collectionId") Long collectionId);

    /** Bộ nào của người này đã có địa điểm (kể cả địa điểm đã bị ẩn — chủ thấy mọi dòng của mình). */
    @Query("""
            SELECT cp.collection.id FROM CollectionPlace cp
            WHERE cp.collection.user.id = :userId AND cp.place.id = :placeId
            """)
    List<Long> findCollectionIdsContaining(@Param("userId") Long userId, @Param("placeId") Long placeId);

    /** Idempotent — trả số dòng đã xóa (0 / 1). */
    @Modifying
    @Query(
            value = "DELETE FROM collection_place WHERE collection_id = :collectionId AND place_id = :placeId",
            nativeQuery = true)
    int deleteItem(@Param("collectionId") Long collectionId, @Param("placeId") Long placeId);
}
