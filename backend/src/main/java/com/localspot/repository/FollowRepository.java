package com.localspot.repository;

import com.localspot.entity.Follow;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Follow.Id> {

    /** Idempotent theo PK(follower_id, following_id); trả 1 khi vừa theo dõi, 0 khi đã theo dõi từ trước. */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO follows (follower_id, following_id, created_at)
            VALUES (:followerId, :followingId, :createdAt)
            """, nativeQuery = true)
    int follow(
            @Param("followerId") Long followerId,
            @Param("followingId") Long followingId,
            @Param("createdAt") Instant createdAt);

    @Modifying
    @Query(
            value = "DELETE FROM follows WHERE follower_id = :followerId AND following_id = :followingId",
            nativeQuery = true)
    int unfollow(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    boolean existsByIdFollowerIdAndIdFollowingId(Long followerId, Long followingId);

    /** Đếm qua bảng users để bỏ tài khoản đã xóa mềm — khớp với danh sách. */
    @Query("SELECT COUNT(f) FROM Follow f JOIN f.follower u WHERE f.following.id = :userId")
    long countFollowers(@Param("userId") Long userId);

    @Query("SELECT COUNT(f) FROM Follow f JOIN f.following u WHERE f.follower.id = :userId")
    long countFollowing(@Param("userId") Long userId);

    /** Người theo dõi {@code userId}, theo dõi gần nhất trước — keyset {@code (created_at, follower_id)}. */
    @Query("""
            SELECT f FROM Follow f JOIN FETCH f.follower
            WHERE f.following.id = :userId
              AND (:at IS NULL OR f.createdAt < :at OR (f.createdAt = :at AND f.follower.id < :otherId))
            ORDER BY f.createdAt DESC, f.follower.id DESC
            """)
    List<Follow> findFollowers(
            @Param("userId") Long userId, @Param("at") Instant at, @Param("otherId") Long otherId, Limit limit);

    /** Người {@code userId} đang theo dõi — dùng {@code PK(follower_id, …)}. */
    @Query("""
            SELECT f FROM Follow f JOIN FETCH f.following
            WHERE f.follower.id = :userId
              AND (:at IS NULL OR f.createdAt < :at OR (f.createdAt = :at AND f.following.id < :otherId))
            ORDER BY f.createdAt DESC, f.following.id DESC
            """)
    List<Follow> findFollowing(
            @Param("userId") Long userId, @Param("at") Instant at, @Param("otherId") Long otherId, Limit limit);
}
