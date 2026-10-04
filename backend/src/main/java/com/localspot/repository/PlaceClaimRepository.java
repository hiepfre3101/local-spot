package com.localspot.repository;

import com.localspot.entity.ClaimStatus;
import com.localspot.entity.PlaceClaim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceClaimRepository extends JpaRepository<PlaceClaim, Long> {

    /** UC23 bước 3: người dùng đã có yêu cầu đang chờ cho địa điểm này chưa — index {@code (place_id, status)}. */
    boolean existsByPlaceIdAndUserIdAndStatus(Long placeId, Long userId, ClaimStatus status);
}
