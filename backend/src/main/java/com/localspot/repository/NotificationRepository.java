package com.localspot.repository;

import com.localspot.entity.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Mới nhất trước ({@code id} tăng theo thời gian); {@code unreadOnly} lọc chưa đọc. Index {@code (user_id, read_at, created_at)}. */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.user.id = :userId AND (:unreadOnly = FALSE OR n.readAt IS NULL)
              AND (:beforeId IS NULL OR n.id < :beforeId)
            ORDER BY n.id DESC
            """)
    List<Notification> findPage(
            @Param("userId") Long userId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("beforeId") Long beforeId,
            Limit limit);

    long countByUserIdAndReadAtIsNull(Long userId);

    /** Chỉ của chính người dùng — thông báo người khác coi như không tồn tại (404). */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** Đánh dấu tất cả đã đọc một câu UPDATE; thông báo đã đọc giữ nguyên thời điểm đọc cũ. */
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.user.id = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
