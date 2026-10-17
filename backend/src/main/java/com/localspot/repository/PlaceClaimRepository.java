package com.localspot.repository;

import com.localspot.entity.ClaimStatus;
import com.localspot.entity.PlaceClaim;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceClaimRepository extends JpaRepository<PlaceClaim, Long> {

    /** UC23 bước 3: người dùng đã có yêu cầu đang chờ cho địa điểm này chưa — index {@code (place_id, status)}. */
    boolean existsByPlaceIdAndUserIdAndStatus(Long placeId, Long userId, ClaimStatus status);

    /** Hàng chờ (FR-40): PENDING, cũ nhất trước, kèm địa điểm + danh mục (thẻ) và người yêu cầu. */
    @Query("""
            SELECT c FROM PlaceClaim c JOIN FETCH c.place p JOIN FETCH p.category JOIN FETCH c.user
            WHERE c.status = com.localspot.entity.ClaimStatus.PENDING AND (:afterId IS NULL OR c.id > :afterId)
            ORDER BY c.id ASC
            """)
    List<PlaceClaim> findPendingOldestFirst(@Param("afterId") Long afterId, Limit limit);

    /** Màn duyệt một yêu cầu: mọi trạng thái, kèm địa điểm + danh mục và người yêu cầu. */
    @Query("""
            SELECT c FROM PlaceClaim c JOIN FETCH c.place p JOIN FETCH p.category JOIN FETCH c.user
            WHERE c.id = :id
            """)
    Optional<PlaceClaim> findDetailById(@Param("id") Long id);

    /**
     * Địa điểm của yêu cầu — truy vấn vô hướng để khóa dòng địa điểm <b>trước</b> khi nạp yêu cầu: nạp yêu cầu trước thì
     * địa điểm đã nằm trong phiên, khóa sau không làm mới được {@code owner_id}.
     */
    @Query("SELECT c.place.id FROM PlaceClaim c WHERE c.id = :id")
    Optional<Long> findPlaceIdById(@Param("id") Long id);

    /** Yêu cầu đang chờ khác cho cùng địa điểm — tự từ chối khi một yêu cầu được duyệt (UC23 quy tắc). */
    @Query("""
            SELECT c FROM PlaceClaim c
            WHERE c.place.id = :placeId AND c.status = com.localspot.entity.ClaimStatus.PENDING AND c.id <> :claimId
            """)
    List<PlaceClaim> findOtherPending(@Param("placeId") Long placeId, @Param("claimId") Long claimId);

    /** Mỗi dòng {@code [claimId, placeName]} — tiêu đề trên hàng chờ gộp, một truy vấn cho cả trang. */
    @Query("SELECT c.id, p.name FROM PlaceClaim c JOIN c.place p WHERE c.id IN :ids")
    List<Object[]> findPlaceNamesByIdIn(@Param("ids") Collection<Long> ids);

    /** Mỗi dòng {@code [placeId, count]}: số yêu cầu đang chờ theo địa điểm — một truy vấn cho cả trang hàng chờ. */
    @Query("""
            SELECT c.place.id, COUNT(c) FROM PlaceClaim c
            WHERE c.place.id IN :placeIds AND c.status = com.localspot.entity.ClaimStatus.PENDING
            GROUP BY c.place.id
            """)
    List<Object[]> countPendingByPlaceIds(@Param("placeIds") Collection<Long> placeIds);
}
