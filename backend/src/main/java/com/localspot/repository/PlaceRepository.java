package com.localspot.repository;

import com.localspot.entity.Place;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

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
}
