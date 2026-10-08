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
}
