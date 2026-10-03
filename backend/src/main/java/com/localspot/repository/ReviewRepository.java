package com.localspot.repository;

import com.localspot.entity.Review;
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
}
