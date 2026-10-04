package com.localspot.repository;

import com.localspot.entity.Place;
import com.localspot.entity.PlaceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn mặc định bỏ qua địa điểm đã xóa mềm ({@code @SQLRestriction} trên {@link Place}). */
public interface PlaceRepository extends JpaRepository<Place, Long>, PlaceSearchRepository {

    /** {@code MeResponse.ownedPlaceIds} — dùng index {@code ix_places_owner_id}. */
    @Query("SELECT p.id FROM Place p WHERE p.owner.id = :ownerId ORDER BY p.id")
    List<Long> findIdsByOwnerId(@Param("ownerId") Long ownerId);

    /**
     * Rỗng = địa điểm không tồn tại / đã xóa; {@code false} = có nhưng không thuộc người dùng (kể cả chưa có chủ —
     * {@code LEFT JOIN} để không mất dòng).
     */
    @Query("""
            SELECT CASE WHEN o.id = :userId THEN TRUE ELSE FALSE END
            FROM Place p LEFT JOIN p.owner o
            WHERE p.id = :placeId
            """)
    Optional<Boolean> isOwnedBy(@Param("placeId") Long placeId, @Param("userId") Long userId);

    /**
     * Slug đã dùng bằng đúng {@code base} hoặc dạng {@code base-<n>}, <b>kể cả địa điểm đã xóa mềm</b> (native — UNIQUE
     * ở CSDL tính cả dòng đã xóa). {@code base} chỉ gồm {@code [a-z0-9-]} nên không chứa ký tự đại diện của LIKE.
     */
    @Query(value = "SELECT slug FROM places WHERE slug = :base OR slug LIKE CONCAT(:base, '-%')", nativeQuery = true)
    List<String> findSlugsLike(@Param("base") String base);

    /** Trang chi tiết: kèm danh mục, chủ, tiện ích (giờ mở cửa nạp lười trong cùng transaction — tránh tích Descartes). */
    @EntityGraph(attributePaths = {"category", "owner", "amenities"})
    Optional<Place> findDetailBySlug(String slug);

    @EntityGraph(attributePaths = {"category", "owner", "amenities"})
    Optional<Place> findDetailById(Long id);

    /** {@code GET /me/places}: địa điểm tôi đề xuất, mọi trạng thái, mới nhất trước — keyset theo id. */
    @Query("""
            SELECT p FROM Place p JOIN FETCH p.category
            WHERE p.createdBy.id = :userId AND (:afterId IS NULL OR p.id < :afterId)
            ORDER BY p.id DESC
            """)
    List<Place> findCreatedBy(@Param("userId") Long userId, @Param("afterId") Long afterId, Limit limit);

    /** {@code GET /owner/places}: số địa điểm một chủ sở hữu nhỏ → trả hết, không phân trang. */
    @Query("SELECT p FROM Place p JOIN FETCH p.category WHERE p.owner.id = :ownerId ORDER BY p.id DESC")
    List<Place> findOwnedBy(@Param("ownerId") Long ownerId);

    /** Hàng chờ PENDING: FIFO — cũ nhất trước, cùng thứ tự với hàng chờ gộp {@code /moderation/queue}. */
    @Query("""
            SELECT p FROM Place p JOIN FETCH p.category
            WHERE p.status = :status AND (:afterId IS NULL OR p.id > :afterId)
            ORDER BY p.id ASC
            """)
    List<Place> findByStatusOldestFirst(
            @Param("status") PlaceStatus status, @Param("afterId") Long afterId, Limit limit);

    /** Lịch sử đã xử lý (APPROVED / REJECTED / HIDDEN): mới nhất trước. */
    @Query("""
            SELECT p FROM Place p JOIN FETCH p.category
            WHERE p.status = :status AND (:afterId IS NULL OR p.id < :afterId)
            ORDER BY p.id DESC
            """)
    List<Place> findByStatusNewestFirst(
            @Param("status") PlaceStatus status, @Param("afterId") Long afterId, Limit limit);
}
