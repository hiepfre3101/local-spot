package com.localspot.repository;

import com.localspot.entity.ActivityLog;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Nhật ký thao tác quản trị (FR-42) — chỉ thêm, ghi qua {@code ActivityLogAspect}. */
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /**
     * Mới nhất trước, keyset theo id. Không nạp {@code actor} (nạp qua entity sẽ bị {@code @SQLRestriction} của
     * {@code User} loại người đã xóa) — chỉ đọc khóa ngoại, tên người thao tác nạp theo lô ở service. Lọc
     * {@code actorId} dùng index {@code (actor_id, created_at)}.
     */
    @Query("""
            SELECT a FROM ActivityLog a
            WHERE (:actorId IS NULL OR a.actor.id = :actorId)
              AND (:action IS NULL OR a.action = :action)
              AND (:afterId IS NULL OR a.id < :afterId)
            ORDER BY a.id DESC
            """)
    List<ActivityLog> findPage(
            @Param("actorId") Long actorId,
            @Param("action") String action,
            @Param("afterId") Long afterId,
            Limit limit);
}
