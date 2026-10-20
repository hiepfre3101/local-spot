package com.localspot.repository;

import com.localspot.entity.UserBadge;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UserBadge.Id> {

    /** Huy hiệu của một người, theo thứ tự đạt được (cùng lúc thì theo thứ tự trong danh mục). */
    @Query("""
            SELECT ub FROM UserBadge ub JOIN FETCH ub.badge b
            WHERE ub.user.id = :userId
            ORDER BY ub.awardedAt, b.id
            """)
    List<UserBadge> findByUser(@Param("userId") Long userId);

    /** Trao các huy hiệu còn thiếu — idempotent theo PK(user_id, badge_id); trả số huy hiệu mới. */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO user_badges (user_id, badge_id, awarded_at)
            SELECT :userId, b.id, :awardedAt FROM badges b WHERE b.code IN (:codes)
            """, nativeQuery = true)
    int award(
            @Param("userId") Long userId,
            @Param("codes") Collection<String> codes,
            @Param("awardedAt") Instant awardedAt);
}
