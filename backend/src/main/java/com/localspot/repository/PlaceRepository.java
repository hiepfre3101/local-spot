package com.localspot.repository;

import com.localspot.entity.Place;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    /** {@code MeResponse.ownedPlaceIds} — dùng index {@code ix_places_owner_id}. */
    @Query("SELECT p.id FROM Place p WHERE p.owner.id = :ownerId ORDER BY p.id")
    List<Long> findIdsByOwnerId(@Param("ownerId") Long ownerId);
}
