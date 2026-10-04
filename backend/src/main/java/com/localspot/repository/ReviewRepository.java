package com.localspot.repository;

import com.localspot.entity.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn mặc định bỏ qua review đã xóa mềm ({@code @SQLRestriction} trên {@link Review}). */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Kiểm tra quyền sở hữu: rỗng = review không tồn tại / đã xóa; {@code false} = có nhưng của người khác. */
    @Query("SELECT CASE WHEN r.user.id = :userId THEN TRUE ELSE FALSE END FROM Review r WHERE r.id = :reviewId")
    Optional<Boolean> isAuthoredBy(@Param("reviewId") Long reviewId, @Param("userId") Long userId);

    /**
     * Người dùng có phải chủ địa điểm của review không (phản hồi review — FR-33). {@code LEFT JOIN} owner: địa điểm chưa
     * có chủ vẫn trả {@code false} thay vì mất dòng (bị hiểu nhầm là review không tồn tại).
     */
    @Query("""
            SELECT CASE WHEN o.id = :userId THEN TRUE ELSE FALSE END
            FROM Review r JOIN r.place p LEFT JOIN p.owner o
            WHERE r.id = :reviewId
            """)
    Optional<Boolean> isOnPlaceOwnedBy(@Param("reviewId") Long reviewId, @Param("userId") Long userId);

    /**
     * Phân bố sao của review PUBLISHED (FR-13) — mỗi dòng {@code [rating, count]}, chỉ có các mức sao có review. Dùng
     * index {@code (place_id, status, created_at)}.
     */
    @Query("""
            SELECT r.rating, COUNT(r) FROM Review r
            WHERE r.place.id = :placeId AND r.status = com.localspot.entity.ReviewStatus.PUBLISHED
            GROUP BY r.rating
            """)
    List<Object[]> countPublishedByRating(@Param("placeId") Long placeId);

    /**
     * Review của người dùng cho địa điểm, <b>kể cả đã xóa mềm</b> (native, bỏ qua {@code @SQLRestriction}): mỗi người
     * một review / địa điểm tính cả review đã xóa (D2) — frontend dựa vào đây để ẩn nút "Viết đánh giá".
     */
    @Query(value = "SELECT id FROM reviews WHERE place_id = :placeId AND user_id = :userId", nativeQuery = true)
    Optional<Long> findIdIncludingDeleted(@Param("placeId") Long placeId, @Param("userId") Long userId);
}
