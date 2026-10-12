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

    /**
     * Một trang bình luận gốc của review, cũ trước (keyset {@code id > :afterId}) — dòng {@code [id, shown, createdAtMs]}.
     * Gồm cả gốc đã xóa / bị ẩn <b>nếu còn trả lời đang hiện</b> ({@code shown = 0} → placeholder "đã bị xóa", chốt
     * 2026-10-11) — native vì {@code @SQLRestriction} sẽ loại các gốc này. Thời điểm trả dạng epoch ms tính trong MySQL
     * (phiên UTC): {@code DATETIME} của native query bị driver đổi sang múi giờ JVM.
     */
    @Query(value = """
            SELECT c.id,
                   (c.deleted_at IS NULL AND c.status = 'VISIBLE') AS shown,
                   CAST(UNIX_TIMESTAMP(c.created_at) * 1000 AS SIGNED) AS created_ms
            FROM comments c
            WHERE c.review_id = :reviewId AND c.parent_id IS NULL
              AND (:afterId IS NULL OR c.id > :afterId)
              AND ((c.deleted_at IS NULL AND c.status = 'VISIBLE')
                   OR EXISTS (SELECT 1 FROM comments r
                              WHERE r.parent_id = c.id AND r.deleted_at IS NULL AND r.status = 'VISIBLE'))
            ORDER BY c.id
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findRootPage(
            @Param("reviewId") Long reviewId, @Param("afterId") Long afterId, @Param("limit") int limit);

    /** Gốc đang hiện trong trang, kèm tác giả. */
    @Query("""
            SELECT c FROM Comment c JOIN FETCH c.user
            WHERE c.id IN :ids AND c.status = com.localspot.entity.CommentStatus.VISIBLE
            """)
    List<Comment> findVisibleWithAuthorByIdIn(@Param("ids") Collection<Long> ids);

    /** Trả lời đang hiện của các gốc trong trang, cũ trước — một truy vấn cho cả trang (không N+1). */
    @Query("""
            SELECT c FROM Comment c JOIN FETCH c.user
            WHERE c.parent.id IN :rootIds AND c.status = com.localspot.entity.CommentStatus.VISIBLE
            ORDER BY c.id
            """)
    List<Comment> findVisibleRepliesWithAuthor(@Param("rootIds") Collection<Long> rootIds);
}
