package com.localspot.repository;

import com.localspot.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn mặc định bỏ qua tài khoản đã xóa mềm ({@code @SQLRestriction} trên {@link User}). */
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    /** Đăng nhập: cần role để dựng MeResponse. */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(Long id);

    /**
     * Nạp quyền cho mỗi request có access token — một truy vấn (user + role + permission). Đọc CSDL mỗi request để khóa
     * tài khoản / gỡ role có hiệu lực ngay, không đợi access token hết hạn.
     */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithAuthoritiesById(Long id);
}
