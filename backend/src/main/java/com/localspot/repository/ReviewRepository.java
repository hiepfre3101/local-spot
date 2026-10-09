package com.localspot.repository;

import com.localspot.entity.Review;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
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

    // ─── Chống review ảo (FR-23) — native: tính cả review đã xóa mềm ─────────

    /**
     * Số review người dùng viết từ {@code since}, <b>kể cả đã xóa</b> — xóa không trả lại lượt (giới hạn đếm số lần viết).
     * Index {@code (user_id, created_at)}.
     */
    @Query(value = "SELECT COUNT(*) FROM reviews WHERE user_id = :userId AND created_at >= :since", nativeQuery = true)
    long countWrittenSince(@Param("userId") Long userId, @Param("since") Instant since);

    /**
     * Thời điểm (epoch ms) của review cũ nhất trong cửa sổ — rời cửa sổ là có lượt mới (header {@code Retry-After}).
     * Tính bằng {@code UNIX_TIMESTAMP} trong MySQL (phiên kết nối ép múi giờ UTC) thay vì trả {@code DATETIME}: native
     * query trả {@code LocalDateTime} đã bị driver đổi sang múi giờ JVM (+07) — lệch 7 giờ (bắt được bằng test).
     */
    @Query(value = """
                    SELECT CAST(UNIX_TIMESTAMP(MIN(created_at)) * 1000 AS SIGNED) FROM reviews
                    WHERE user_id = :userId AND created_at >= :since
                    """, nativeQuery = true)
    Optional<Long> findOldestWrittenEpochMillisSince(@Param("userId") Long userId, @Param("since") Instant since);

    /** Số review địa điểm nhận từ một dải IP từ {@code since} (U3), kể cả đã xóa. Index {@code (place_id, ip_prefix, created_at)}. */
    @Query(value = """
                    SELECT COUNT(*) FROM reviews
                    WHERE place_id = :placeId AND ip_prefix = :ipPrefix AND created_at >= :since
                    """, nativeQuery = true)
    long countFromIpPrefixSince(
            @Param("placeId") Long placeId, @Param("ipPrefix") String ipPrefix, @Param("since") Instant since);

    // ─── Danh sách (FR-14) — keyset, kèm tác giả ─────────────────────────────

    /** Review PUBLISHED của địa điểm, mới nhất trước ({@code id} tăng theo thời điểm tạo). */
    @Query("""
            SELECT r FROM Review r JOIN FETCH r.user
            WHERE r.place.id = :placeId AND r.status = com.localspot.entity.ReviewStatus.PUBLISHED
              AND (:rating IS NULL OR r.rating = :rating)
              AND (:afterId IS NULL OR r.id < :afterId)
            ORDER BY r.id DESC
            """)
    List<Review> findPublishedNewest(
            @Param("placeId") Long placeId,
            @Param("rating") Integer rating,
            @Param("afterId") Long afterId,
            Limit limit);

    /** Hữu ích nhất trước — keyset tổ hợp {@code (helpful_count, id)}. */
    @Query("""
            SELECT r FROM Review r JOIN FETCH r.user
            WHERE r.place.id = :placeId AND r.status = com.localspot.entity.ReviewStatus.PUBLISHED
              AND (:rating IS NULL OR r.rating = :rating)
              AND (:afterId IS NULL OR r.helpfulCount < :afterHelpful
                   OR (r.helpfulCount = :afterHelpful AND r.id < :afterId))
            ORDER BY r.helpfulCount DESC, r.id DESC
            """)
    List<Review> findPublishedMostHelpful(
            @Param("placeId") Long placeId,
            @Param("rating") Integer rating,
            @Param("afterHelpful") Integer afterHelpful,
            @Param("afterId") Long afterId,
            Limit limit);

    /** {@code GET /me/reviews}: mọi trạng thái (UC12 — người dùng thấy review chờ duyệt / bị từ chối của mình). */
    @Query("""
            SELECT r FROM Review r JOIN FETCH r.user
            WHERE r.user.id = :userId AND (:afterId IS NULL OR r.id < :afterId)
            ORDER BY r.id DESC
            """)
    List<Review> findWrittenBy(@Param("userId") Long userId, @Param("afterId") Long afterId, Limit limit);

    /** {@code GET /users/{id}/reviews}: chỉ PUBLISHED. Index {@code (user_id, created_at)}. */
    @Query("""
            SELECT r FROM Review r JOIN FETCH r.user
            WHERE r.user.id = :userId AND r.status = com.localspot.entity.ReviewStatus.PUBLISHED
              AND (:afterId IS NULL OR r.id < :afterId)
            ORDER BY r.id DESC
            """)
    List<Review> findPublishedBy(@Param("userId") Long userId, @Param("afterId") Long afterId, Limit limit);

    @Query("SELECT r FROM Review r JOIN FETCH r.user WHERE r.id = :id")
    Optional<Review> findWithAuthorById(@Param("id") Long id);
}
