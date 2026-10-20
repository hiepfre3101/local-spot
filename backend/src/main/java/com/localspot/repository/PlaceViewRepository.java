package com.localspot.repository;

import com.localspot.entity.PlaceView;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceViewRepository extends JpaRepository<PlaceView, PlaceView.Id> {

    /**
     * +1 lượt xem cho (địa điểm, ngày) — upsert nguyên tử: nhiều request đồng thời không mất lượt, không lỗi trùng khóa
     * khi dòng của ngày chưa có (database.md §3.2 {@code place_views}).
     */
    @Modifying
    @Query(value = """
            INSERT INTO place_views (place_id, view_date, view_count) VALUES (:placeId, :date, 1)
            ON DUPLICATE KEY UPDATE view_count = view_count + 1
            """, nativeQuery = true)
    void increment(@Param("placeId") Long placeId, @Param("date") LocalDate date);
}
