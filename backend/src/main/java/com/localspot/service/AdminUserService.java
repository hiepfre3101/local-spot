package com.localspot.service;

import com.localspot.audit.AuditedAction;
import com.localspot.dto.response.AdminUserResponse;
import com.localspot.dto.response.CursorPage;
import com.localspot.entity.Role;
import com.localspot.entity.User;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.mapper.UserMapper;
import com.localspot.repository.RoleRepository;
import com.localspot.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý người dùng & vai trò (UC31, FR-38). Kiểm tra quyền (permission) nằm ở {@code @PreAuthorize} của controller;
 * service giữ luật nghiệp vụ đã chốt 2026-10-03:
 *
 * <ul>
 *   <li>Mọi tài khoản luôn có USER — nhân sự kiểm duyệt / quản trị vẫn viết review như thành viên.
 *   <li>OWNER không gán / gỡ ở đây: OWNER đi cùng {@code places.owner_id}, chỉ cấp qua duyệt yêu cầu sở hữu (UC30). Gỡ
 *       tay sẽ để lại chủ địa điểm mất quyền sửa nhưng vẫn là {@code owner_id}.
 *   <li>Admin không tự khóa mình, không tự gỡ ADMIN của mình → luôn còn ít nhất một admin (người đang thao tác). Admin
 *       vẫn khóa / hạ quyền admin khác được.
 * </ul>
 *
 * Gỡ role / khóa có hiệu lực ngay ở request kế tiếp vì quyền được nạp từ CSDL mỗi request
 * ({@code UserJwtAuthenticationConverter}) — không cần chờ access token hết hạn. Mọi thao tác ghi được ghi nhật ký
 * quản trị qua {@link AuditedAction} (FR-42).
 */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenService refreshTokens;
    private final TrustScoreService trustScores;
    private final UserMapper mapper;
    private final Clock clock;

    public AdminUserService(
            UserRepository users,
            RoleRepository roles,
            RefreshTokenService refreshTokens,
            TrustScoreService trustScores,
            UserMapper mapper,
            Clock clock) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.trustScores = trustScores;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Tìm theo email / tên hiển thị, lọc role và trạng thái khóa; mới nhất trước. */
    @Transactional(readOnly = true)
    public CursorPage<AdminUserResponse> search(
            String query, String role, Boolean locked, String cursor, Integer requestedLimit) {
        if (role != null && !Role.NAMES.contains(role)) {
            throw ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "role", "Vai trò không tồn tại: " + role);
        }
        int limit = KeysetCursor.limit(requestedLimit);
        // Lấy dư một dòng để biết còn trang sau mà không cần COUNT
        List<Long> ids = users.searchIds(
                likePattern(query), role, locked, clock.instant(), KeysetCursor.decode(cursor), Limit.of(limit + 1));
        boolean hasMore = ids.size() > limit;
        List<Long> pageIds = hasMore ? ids.subList(0, limit) : ids;
        if (pageIds.isEmpty()) {
            return new CursorPage<>(List.of(), null);
        }

        List<User> page = users.findWithRolesByIdIn(pageIds).stream()
                .sorted(Comparator.comparing(User::getId).reversed())
                .toList();
        Map<Long, Integer> trust = trustScores.trustScoresOf(page);
        List<AdminUserResponse> items = page.stream()
                .map(u -> mapper.toAdminUserResponse(u, trust.get(u.getId())))
                .toList();
        return new CursorPage<>(items, hasMore ? KeysetCursor.encode(pageIds.getLast()) : null);
    }

    /** Khóa tới {@code until} + thu hồi mọi refresh token; access token đang giữ bị chặn ngay ở request kế tiếp. */
    @Transactional
    @AuditedAction(
            action = "USER_LOCK",
            targetType = "USER",
            targetId = "#userId",
            metadata = "{until: #until.toString(), reason: #reason.strip()}")
    public void lock(Long actorId, Long userId, Instant until, String reason) {
        if (actorId.equals(userId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ErrorCode.SELF_ACTION_FORBIDDEN, "Không thể tự khóa tài khoản của mình.");
        }
        User user = users.findById(userId).orElseThrow(AdminUserService::userNotFound);
        // Khóa lại người đang bị khóa = cập nhật thời hạn / lý do (gia hạn hoặc rút ngắn)
        user.setLockedUntil(until);
        user.setLockReason(reason.strip());
        refreshTokens.revokeAll(user.getId());
    }

    /** Idempotent: mở khóa tài khoản không bị khóa vẫn 204. */
    @Transactional
    @AuditedAction(action = "USER_UNLOCK", targetType = "USER", targetId = "#userId")
    public void unlock(Long userId) {
        User user = users.findById(userId).orElseThrow(AdminUserService::userNotFound);
        user.setLockedUntil(null);
        user.setLockReason(null);
    }

    /** Thay toàn bộ danh sách role (PUT). Xem luật ở javadoc lớp. */
    @Transactional
    @AuditedAction(
            action = "USER_ASSIGN_ROLES",
            targetType = "USER",
            targetId = "#userId",
            metadata = "{before: #result.before(), after: #result.after()}")
    public RoleChange replaceRoles(Long actorId, Long userId, List<String> requested) {
        Set<String> names = validateRoleNames(requested);
        User user = users.findWithRolesById(userId).orElseThrow(AdminUserService::userNotFound);
        Set<String> current = new HashSet<>();
        user.getRoles().forEach(r -> current.add(r.getName()));

        if (names.contains(Role.OWNER) != current.contains(Role.OWNER)) {
            throw ApiException.fieldError(
                    ErrorCode.OWNER_ROLE_MANAGED_BY_CLAIM,
                    "roles",
                    "Vai trò Chủ địa điểm chỉ được cấp qua duyệt yêu cầu sở hữu, không gán hay gỡ tại đây.");
        }
        if (actorId.equals(userId) && current.contains(Role.ADMIN) && !names.contains(Role.ADMIN)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.SELF_ACTION_FORBIDDEN,
                    "Không thể tự gỡ vai trò Quản trị viên của mình.");
        }

        user.getRoles().clear();
        user.getRoles().addAll(roles.findByNameIn(names));
        return new RoleChange(List.copyOf(new TreeSet<>(current)), List.copyOf(names));
    }

    /** Danh sách role trước / sau khi thay (sắp xếp tên) — nhật ký quản trị lưu cả hai (database.md §3, FR-42). */
    public record RoleChange(List<String> before, List<String> after) {

        public RoleChange {
            before = List.copyOf(before);
            after = List.copyOf(after);
        }
    }

    private static Set<String> validateRoleNames(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "roles", "Cần ít nhất một vai trò.");
        }
        Set<String> names = new TreeSet<>();
        for (String name : requested) {
            if (name == null || !Role.NAMES.contains(name)) {
                throw ApiException.fieldError(ErrorCode.VALIDATION_FAILED, "roles", "Vai trò không tồn tại: " + name);
            }
            names.add(name);
        }
        if (!names.contains(Role.USER)) {
            throw ApiException.fieldError(
                    ErrorCode.ROLE_USER_REQUIRED, "roles", "Mọi tài khoản phải giữ vai trò Thành viên (USER).");
        }
        return names;
    }

    /** {@code %q%} với {@code ! % _} được thoát (ESCAPE '!') — người dùng gõ "50%" tìm đúng chuỗi "50%". */
    private static String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String escaped = query.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng.");
    }
}
