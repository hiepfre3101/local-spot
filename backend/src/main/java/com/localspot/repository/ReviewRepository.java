package com.localspot.repository;

import com.localspot.entity.Review;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Review mọi người đang thấy: PUBLISHED trên địa điểm APPROVED (cùng điều kiện với danh sách review). Vote / bình
     * luận chỉ áp lên review này — review khác coi như không tồn tại (404).
     */
    @Query("""
            SELECT r FROM Review r JOIN FETCH r.user
            WHERE r.id = :id AND r.status = com.localspot.entity.ReviewStatus.PUBLISHED
              AND r.place.status = com.localspot.entity.PlaceStatus.APPROVED
            """)
    Optional<Review> findVisibleById(@Param("id") Long id);

    /**
     * Đánh giá nổi bật của trang chủ (ReviewCard, FR-08): review đang hiện (PUBLISHED trên địa điểm APPROVED) từ
     * {@code minRating} sao, viết từ {@code since}, nhiều lượt hữu ích nhất — cùng số lượt thì mới hơn trước. Chỉ chạy khi
     * cache trang chủ hết hạn / bị xóa.
     */
    @Query("""
            SELECT r FROM Review r JOIN FETCH r.user JOIN FETCH r.place p
            WHERE r.status = com.localspot.entity.ReviewStatus.PUBLISHED
              AND p.status = com.localspot.entity.PlaceStatus.APPROVED
              AND r.rating >= :minRating AND r.createdAt >= :since
            ORDER BY r.helpfulCount DESC, r.id DESC
            """)
    List<Review> findMostHelpfulVisible(@Param("minRating") int minRating, @Param("since") Instant since, Limit limit);

    // ─── Tính lại rating (FR-22) ────────────────────────────────────────────

    /**
     * C — trung bình sao của mọi review PUBLISHED trên địa điểm APPROVED (mỗi review cùng trọng số, chốt 2026-10-11);
     * {@code null} khi hệ thống chưa có review nào.
     */
    @Query("""
            SELECT AVG(r.rating) FROM Review r
            WHERE r.status = com.localspot.entity.ReviewStatus.PUBLISHED
              AND r.place.status = com.localspot.entity.PlaceStatus.APPROVED
            """)
    Double findSystemAverageRating();

    /** v và R của một địa điểm — index {@code (place_id, status, created_at)}. */
    @Query("""
            SELECT new com.localspot.repository.PlaceRatingStats(COUNT(r), AVG(r.rating)) FROM Review r
            WHERE r.place.id = :placeId AND r.status = com.localspot.entity.ReviewStatus.PUBLISHED
            """)
    PlaceRatingStats findRatingStats(@Param("placeId") Long placeId);

    // ─── Vote hữu ích (FR-20) — đếm nguyên tử, không qua entity ─────────────

    /**
     * Khóa độc quyền dòng review ({@code FOR UPDATE}) và đọc trạng thái <b>mới nhất</b> — truy vấn vô hướng nên không bị
     * bản entity đã nạp trước trong phiên che mất. Rỗng = không tồn tại / đã xóa mềm.
     */
    @Query(value = "SELECT status FROM reviews WHERE id = :id AND deleted_at IS NULL FOR UPDATE", nativeQuery = true)
    Optional<String> lockStatusById(@Param("id") Long id);

    /** {@code helpful_count} mới nhất trong CSDL (bỏ qua bản trong bộ nhớ) — trả về sau khi vote / bỏ vote. */
    @Query(value = "SELECT helpful_count FROM reviews WHERE id = :id", nativeQuery = true)
    int findHelpfulCount(@Param("id") Long id);

    /** Không đổi {@code updated_at} / {@code version}: vote không phải là sửa review, không làm hỏng bản sửa của tác giả. */
    @Modifying
    @Query(value = "UPDATE reviews SET helpful_count = helpful_count + 1 WHERE id = :id", nativeQuery = true)
    void incrementHelpfulCount(@Param("id") Long id);

    /** Chặn dưới 0 (cột UNSIGNED) — phòng thủ, bình thường mỗi lần trừ đi đôi với một vote vừa xóa. */
    @Modifying
    @Query(
            value = "UPDATE reviews SET helpful_count = helpful_count - 1 WHERE id = :id AND helpful_count > 0",
            nativeQuery = true)
    void decrementHelpfulCount(@Param("id") Long id);

    /** Khóa dòng review khi thêm / xóa ảnh: hai lần upload đồng thời lần lượt đếm giới hạn ảnh mỗi review. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r WHERE r.id = :id")
    Optional<Review> findByIdForUpdate(@Param("id") Long id);

    /** Tác giả của review, kể cả review đã xóa mềm — listener điểm đóng góp sau khi review đổi trạng thái. */
    @Query(value = "SELECT user_id FROM reviews WHERE id = :id", nativeQuery = true)
    Optional<Long> findAuthorIdIncludingDeleted(@Param("id") Long id);

    /** Hàng đợi báo cáo (UC29): review theo id ở mọi trạng thái, kèm địa điểm và tác giả — review đã xóa bị bỏ qua. */
    @Query("SELECT r FROM Review r JOIN FETCH r.place JOIN FETCH r.user WHERE r.id IN :ids")
    List<Review> findWithPlaceAndAuthorByIdIn(@Param("ids") Collection<Long> ids);

    /** Tác giả các review đang PUBLISHED của địa điểm — điểm đóng góp của họ đổi khi địa điểm bị ẩn (UC29). */
    @Query(value = """
            SELECT DISTINCT user_id FROM reviews
            WHERE place_id = :placeId AND status = 'PUBLISHED' AND deleted_at IS NULL
            """, nativeQuery = true)
    List<Long> findPublishedAuthorIdsOfPlace(@Param("placeId") Long placeId);

    // ─── Hàng chờ duyệt review (UC28, FR-36) ────────────────────────────────

    /**
     * Review PENDING trên địa điểm APPROVED, cũ nhất trước — kèm tác giả, địa điểm, danh mục cho thẻ. Review trên địa
     * điểm đã bị ẩn không vào hàng chờ: duyệt cũng không ai thấy. Index {@code (status, created_at)}.
     */
    @Query("""
            SELECT r FROM Review r JOIN FETCH r.user JOIN FETCH r.place p JOIN FETCH p.category
            WHERE r.status = com.localspot.entity.ReviewStatus.PENDING
              AND p.status = com.localspot.entity.PlaceStatus.APPROVED
              AND (:ipFlagged IS NULL OR r.ipFlagged = :ipFlagged)
              AND (:afterId IS NULL OR r.id > :afterId)
            ORDER BY r.id ASC
            """)
    List<Review> findModerationQueue(
            @Param("ipFlagged") Boolean ipFlagged, @Param("afterId") Long afterId, Limit limit);

    /** Màn duyệt một review: mọi trạng thái (kể cả đã xử lý — để hiện kết quả), kèm tác giả, địa điểm, danh mục. */
    @Query("SELECT r FROM Review r JOIN FETCH r.user JOIN FETCH r.place p JOIN FETCH p.category WHERE r.id = :id")
    Optional<Review> findForModerationById(@Param("id") Long id);

    /**
     * Mỗi dòng {@code [reviewId, count]}: số review khác cùng địa điểm, cùng dải IP (U3), viết trong {@code windowSeconds}
     * trước hoặc sau review đó — <b>kể cả đã xóa</b>, như luật gắn cờ. Review không có review cùng dải nào không có dòng.
     * Một truy vấn cho cả trang hàng chờ; index {@code (place_id, ip_prefix, created_at)}.
     */
    @Query(value = """
            SELECT r.id, COUNT(o.id) FROM reviews r
            JOIN reviews o ON o.place_id = r.place_id AND o.ip_prefix = r.ip_prefix AND o.id <> r.id
                          AND o.created_at BETWEEN r.created_at - INTERVAL :windowSeconds SECOND
                                               AND r.created_at + INTERVAL :windowSeconds SECOND
            WHERE r.id IN (:ids)
            GROUP BY r.id
            """, nativeQuery = true)
    List<Object[]> countSameSubnetNearby(
            @Param("ids") Collection<Long> ids, @Param("windowSeconds") long windowSeconds);

    /** Các review khác cùng địa điểm, cùng dải IP trong khoảng thời gian (màn duyệt — UC28 bước 2), cũ nhất trước. */
    @Query("""
            SELECT o FROM Review o JOIN FETCH o.user
            WHERE o.place.id = :placeId AND o.ipPrefix = :ipPrefix AND o.id <> :reviewId
              AND o.createdAt BETWEEN :from AND :to
            ORDER BY o.id ASC
            """)
    List<Review> findSameSubnetBetween(
            @Param("placeId") Long placeId,
            @Param("ipPrefix") String ipPrefix,
            @Param("reviewId") Long reviewId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Limit limit);

    /** Lịch sử tác giả (UC28 bước 2): mỗi dòng {@code [status, count]} của các review chưa xóa. */
    @Query("SELECT r.status, COUNT(r) FROM Review r WHERE r.user.id = :userId GROUP BY r.status")
    List<Object[]> countByStatusOfAuthor(@Param("userId") Long userId);
}
