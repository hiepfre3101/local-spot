package com.localspot.repository;

import com.localspot.entity.Amenity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AmenityRepository extends JpaRepository<Amenity, Long> {

    List<Amenity> findAllByOrderByIdAsc();

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    /**
     * Native — tính cả địa điểm đã xóa mềm. Khóa ngoại {@code place_amenity.amenity_id} là {@code ON DELETE CASCADE}: xóa
     * tiện ích đang dùng sẽ âm thầm gỡ nó khỏi các địa điểm, nên service phải chặn ở đây.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM place_amenity WHERE amenity_id = :amenityId)", nativeQuery = true)
    long countPlacesUsing(@Param("amenityId") Long amenityId);
}
