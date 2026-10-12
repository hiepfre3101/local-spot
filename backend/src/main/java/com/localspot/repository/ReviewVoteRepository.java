package com.localspot.repository;

import com.localspot.entity.ReviewVote;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewVoteRepository extends JpaRepository<ReviewVote, ReviewVote.Id> {

    /** Review nào trong trang người xem đã vote "hữu ích" ({@code votedByMe}) — một truy vấn cho cả trang. */
    @Query("SELECT v.id.reviewId FROM ReviewVote v WHERE v.id.userId = :userId AND v.id.reviewId IN :reviewIds")
    List<Long> findVotedReviewIds(@Param("userId") Long userId, @Param("reviewIds") Collection<Long> reviewIds);

    /**
     * Thêm vote nếu chưa có — trả 1 khi vừa thêm, 0 khi đã vote (khóa chính {@code (review_id, user_id)}). {@code INSERT
     * IGNORE} làm PUT idempotent kể cả khi hai request của cùng một người chen nhau: request sau không lỗi trùng khóa mà
     * chỉ nhận 0. {@code IGNORE} cũng nuốt lỗi khóa ngoại, nhưng review và người vote đã được kiểm tra tồn tại trong
     * cùng transaction (review chỉ xóa mềm — dòng vẫn còn).
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO review_votes (review_id, user_id, counted, created_at)
            VALUES (:reviewId, :userId, :counted, :createdAt)
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("reviewId") Long reviewId,
            @Param("userId") Long userId,
            @Param("counted") boolean counted,
            @Param("createdAt") Instant createdAt);

    /**
     * Xóa vote theo đúng giá trị {@code counted} — trả số dòng đã xóa (0 / 1). Tách theo {@code counted} để biết có phải
     * trừ {@code users.helpful_votes_count} không mà không cần đọc trước (đọc rồi xóa là hai bước, request chen giữa
     * sẽ trừ hai lần).
     */
    @Modifying
    @Query(
            value = "DELETE FROM review_votes WHERE review_id = :reviewId AND user_id = :userId AND counted = :counted",
            nativeQuery = true)
    int deleteVote(@Param("reviewId") Long reviewId, @Param("userId") Long userId, @Param("counted") boolean counted);
}
