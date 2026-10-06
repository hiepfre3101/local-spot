package com.localspot.repository;

import com.localspot.entity.PhotoStatus;
import com.localspot.entity.PlacePhoto;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacePhotoRepository extends JpaRepository<PlacePhoto, Long> {

    /** Gallery theo thứ tự hiển thị — index {@code (place_id, sort_order)}. */
    List<PlacePhoto> findByPlaceIdOrderBySortOrderAscIdAsc(Long placeId);

    Optional<PlacePhoto> findByIdAndPlaceId(Long id, Long placeId);

    /** Ảnh tính vào giới hạn mỗi địa điểm: đang xử lý + đã xong (ảnh lỗi không chiếm chỗ). */
    long countByPlaceIdAndStatusNot(Long placeId, PhotoStatus status);

    @Query("SELECT COALESCE(MAX(p.sortOrder), -1) FROM PlacePhoto p WHERE p.place.id = :placeId")
    int maxSortOrder(@Param("placeId") Long placeId);

    boolean existsByPlaceIdAndCoverTrue(Long placeId);

    /** Ảnh bìa đã xử lý xong của nhiều địa điểm một lần — thẻ trong danh sách không truy vấn từng địa điểm (N+1). */
    @Query("""
            SELECT p FROM PlacePhoto p
            WHERE p.place.id IN :placeIds AND p.cover = TRUE AND p.status = com.localspot.entity.PhotoStatus.READY
            """)
    List<PlacePhoto> findReadyCovers(@Param("placeIds") Collection<Long> placeIds);
}
