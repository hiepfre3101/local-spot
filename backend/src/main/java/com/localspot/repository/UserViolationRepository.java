package com.localspot.repository;

import com.localspot.entity.UserViolation;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserViolationRepository extends JpaRepository<UserViolation, Long> {

    /** penalty_score của trust: tổng điểm phạt còn hiệu lực — dùng index (user_id, expires_at). */
    @Query("SELECT COALESCE(SUM(v.points), 0) FROM UserViolation v WHERE v.user.id = :userId AND v.expiresAt > :now")
    long sumActivePoints(@Param("userId") Long userId, @Param("now") Instant now);

    /**
     * Như {@link #sumActivePoints} cho nhiều người dùng trong một truy vấn (danh sách quản trị) — tránh N+1. Người
     * không có vi phạm còn hiệu lực không có dòng nào.
     */
    @Query("""
            SELECT new com.localspot.repository.UserActivePoints(v.user.id, SUM(v.points))
            FROM UserViolation v
            WHERE v.user.id IN :userIds AND v.expiresAt > :now
            GROUP BY v.user.id
            """)
    List<UserActivePoints> sumActivePointsByUserIds(
            @Param("userIds") Collection<Long> userIds, @Param("now") Instant now);
}
