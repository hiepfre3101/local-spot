package com.localspot.repository;

import com.localspot.entity.User;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn mặc định bỏ qua tài khoản đã xóa mềm ({@code @SQLRestriction} trên {@link User}). */
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    /** Đăng nhập: cần role + permission để dựng MeResponse (một truy vấn, không N+1 theo role). */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithRolesByEmail(String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithRolesById(Long id);

    /**
     * Nạp quyền cho mỗi request có access token — một truy vấn (user + role + permission). Đọc CSDL mỗi request để khóa
     * tài khoản / gỡ role có hiệu lực ngay, không đợi access token hết hạn.
     */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithAuthoritiesById(Long id);

    /**
     * Tìm kiếm cho màn quản trị (UC31), keyset theo id giảm dần. Chỉ trả id: nạp kèm {@code roles} (collection) trong
     * cùng truy vấn có LIMIT sẽ buộc Hibernate phân trang trong bộ nhớ — nên tách bước 2 {@link #findWithRolesByIdIn}.
     * Tham số null = bỏ điều kiện đó. {@code pattern} đã thoát ký tự đại diện bằng {@code !}; collation
     * {@code utf8mb4_0900_ai_ci} nên so khớp không phân biệt hoa thường / dấu ("quan tri" khớp "Quản Trị").
     */
    @Query("""
            SELECT u.id FROM User u
            WHERE (:pattern IS NULL OR u.email LIKE :pattern ESCAPE '!' OR u.displayName LIKE :pattern ESCAPE '!')
              AND (:role IS NULL
                   OR EXISTS (SELECT 1 FROM User u2 JOIN u2.roles r WHERE u2.id = u.id AND r.name = :role))
              AND (:locked IS NULL
                   OR (:locked = TRUE AND u.lockedUntil > :now)
                   OR (:locked = FALSE AND (u.lockedUntil IS NULL OR u.lockedUntil <= :now)))
              AND (:afterId IS NULL OR u.id < :afterId)
            ORDER BY u.id DESC
            """)
    List<Long> searchIds(
            @Param("pattern") String pattern,
            @Param("role") String role,
            @Param("locked") Boolean locked,
            @Param("now") Instant now,
            @Param("afterId") Long afterId,
            Limit limit);

    @EntityGraph(attributePaths = "roles")
    List<User> findWithRolesByIdIn(Collection<Long> ids);

    /**
     * Khóa dòng người dùng ({@code SELECT … FOR UPDATE}) khi viết review: hai request đồng thời của cùng một người phải
     * lần lượt đếm giới hạn 5 review / 24 giờ, không cùng thấy "còn lượt" rồi cùng vượt.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * Khóa nhiều dòng người dùng theo thứ tự id tăng dần (InnoDB khóa theo thứ tự quét khóa chính) — mọi transaction
     * khóa cùng một tập theo cùng một thứ tự nên không thể chờ vòng tròn. Dùng khi vote: xem {@code ReviewVoteService}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id IN :ids ORDER BY u.id")
    List<User> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    // ─── Vote hữu ích hợp lệ (requirements §5.1) — đếm nguyên tử, không qua entity ─

    @Modifying
    @Query(value = "UPDATE users SET helpful_votes_count = helpful_votes_count + 1 WHERE id = :id", nativeQuery = true)
    void incrementHelpfulVotesCount(@Param("id") Long id);

    @Modifying
    @Query(value = """
            UPDATE users SET helpful_votes_count = helpful_votes_count - 1
            WHERE id = :id AND helpful_votes_count > 0
            """, nativeQuery = true)
    void decrementHelpfulVotesCount(@Param("id") Long id);

    // ─── Điểm đóng góp (FR-29) ──────────────────────────────────────────────

    /** Ghi điểm vừa tính lại từ đầu — trả số dòng đổi (0 khi điểm không đổi). */
    @Modifying
    @Query(value = """
            UPDATE users SET contribution_points = :points WHERE id = :id AND contribution_points <> :points
            """, nativeQuery = true)
    int updateContributionPoints(@Param("id") Long id, @Param("points") int points);

    /** Mọi tài khoản chưa xóa, tăng dần — job đêm đối soát điểm / huy hiệu. */
    @Query(value = "SELECT id FROM users WHERE deleted_at IS NULL ORDER BY id", nativeQuery = true)
    List<Long> findAllActiveIds();

    /** Người dùng (chưa xóa) có permission qua một role nào đó — gửi thông báo cho đội kiểm duyệt (UC23 bước 5). */
    @Query("SELECT DISTINCT u.id FROM User u JOIN u.roles r JOIN r.permissions p WHERE p.name = :permission")
    List<Long> findIdsWithPermission(@Param("permission") String permission);

    /**
     * Mỗi dòng {@code [id, displayName, avatarUrl]}, <b>kể cả tài khoản đã xóa</b> (bỏ qua {@code @SQLRestriction}) —
     * nhật ký quản trị phải còn tên người thao tác dù tài khoản đó bị xóa sau này (tên đã ẩn danh hóa theo D7).
     */
    @Query(value = "SELECT id, display_name, avatar_url FROM users WHERE id IN (:ids)", nativeQuery = true)
    List<Object[]> findSummaryRowsIncludingDeleted(@Param("ids") Collection<Long> ids);
}
