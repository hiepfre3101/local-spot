package com.localspot.repository;

import com.localspot.entity.Comment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn mặc định bỏ qua bình luận đã xóa mềm ({@code @SQLRestriction} trên {@link Comment}). */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** Rỗng = bình luận không tồn tại / đã xóa; {@code false} = có nhưng của người khác. */
    @Query("SELECT CASE WHEN c.user.id = :userId THEN TRUE ELSE FALSE END FROM Comment c WHERE c.id = :commentId")
    Optional<Boolean> isAuthoredBy(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /** Số bình luận đang hiện của từng review (cả trả lời) — dòng {@code [reviewId, count]}, review không có thì vắng. */
    @Query("""
            SELECT c.review.id, COUNT(c) FROM Comment c
            WHERE c.review.id IN :reviewIds AND c.status = com.localspot.entity.CommentStatus.VISIBLE
            GROUP BY c.review.id
            """)
    List<Object[]> countVisibleByReviewIds(@Param("reviewIds") Collection<Long> reviewIds);
}
