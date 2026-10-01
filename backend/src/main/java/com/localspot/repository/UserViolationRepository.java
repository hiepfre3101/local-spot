package com.localspot.repository;

import com.localspot.entity.UserViolation;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserViolationRepository extends JpaRepository<UserViolation, Long> {

    /** penalty_score của trust: tổng điểm phạt còn hiệu lực — dùng index (user_id, expires_at). */
    @Query("SELECT COALESCE(SUM(v.points), 0) FROM UserViolation v WHERE v.user.id = :userId AND v.expiresAt > :now")
    long sumActivePoints(@Param("userId") Long userId, @Param("now") Instant now);
}
