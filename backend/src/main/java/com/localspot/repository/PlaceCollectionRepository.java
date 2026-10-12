package com.localspot.repository;

import com.localspot.entity.PlaceCollection;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceCollectionRepository extends JpaRepository<PlaceCollection, Long> {

    long countByUserId(Long userId);

    /** Khóa dòng bộ sưu tập: thêm địa điểm đồng thời xếp hàng ở đây → trần số địa điểm đếm đúng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PlaceCollection c WHERE c.id = :id")
    Optional<PlaceCollection> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT c FROM PlaceCollection c JOIN FETCH c.user WHERE c.id = :id")
    Optional<PlaceCollection> findWithOwnerById(@Param("id") Long id);

    @Query("SELECT c FROM PlaceCollection c JOIN FETCH c.user WHERE c.shareSlug = :slug AND c.publicCollection = TRUE")
    Optional<PlaceCollection> findPublicBySlug(@Param("slug") String slug);

    /** Mới tạo trước; tối đa 50 / người nên không phân trang. Dùng {@code ix_collections_user_id}. */
    @Query("""
            SELECT c FROM PlaceCollection c
            WHERE c.user.id = :userId AND (:publicOnly = FALSE OR c.publicCollection = TRUE)
            ORDER BY c.id DESC
            """)
    List<PlaceCollection> findByOwner(@Param("userId") Long userId, @Param("publicOnly") boolean publicOnly);

    /**
     * Số địa điểm và địa điểm làm ảnh bìa (thêm gần nhất) của nhiều bộ — một truy vấn. {@code approvedOnly}: người xem
     * không phải chủ chỉ thấy địa điểm đang hiện (chốt 2026-10-12); chủ thấy cả địa điểm đã bị ẩn sau khi thêm.
     * Hàng: {@code [collection_id, place_count, cover_place_id]}.
     */
    @Query(value = """
            SELECT cp.collection_id, COUNT(*),
                   CAST(SUBSTRING_INDEX(GROUP_CONCAT(cp.place_id ORDER BY cp.added_at DESC, cp.place_id DESC), ',', 1)
                        AS UNSIGNED)
            FROM collection_place cp JOIN places p ON p.id = cp.place_id
            WHERE cp.collection_id IN (:ids) AND p.deleted_at IS NULL
              AND (:approvedOnly = FALSE OR p.status = 'APPROVED')
            GROUP BY cp.collection_id
            """, nativeQuery = true)
    List<Object[]> placeStats(@Param("ids") Collection<Long> ids, @Param("approvedOnly") boolean approvedOnly);
}
