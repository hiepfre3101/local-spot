package com.localspot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localspot.TestcontainersConfiguration;
import com.localspot.exception.ApiException;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

/**
 * Quyền sở hữu qua đúng đường chạy thật: {@code @PreAuthorize("hasPermission(...)")} → expression handler →
 * {@link OwnershipPermissionEvaluator} → truy vấn MySQL; authority nạp từ CSDL bằng
 * {@link UserJwtAuthenticationConverter} như một request có access token. Endpoint thật dùng evaluator làm ở mục E —
 * ở đây dùng bean {@link Guarded} chỉ có trong test, chú thích giống hệt cách endpoint sẽ dùng.
 */
@Import({TestcontainersConfiguration.class, OwnershipPermissionEvaluatorTests.GuardedConfig.class})
@SpringBootTest
@ActiveProfiles("test")
class OwnershipPermissionEvaluatorTests {

    @Autowired
    private Guarded guarded;

    @Autowired
    private OwnershipPermissionEvaluator evaluator;

    @Autowired
    private UserJwtAuthenticationConverter converter;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ─── Review ──────────────────────────────────────────────────────────────

    @Test
    void onlyTheAuthorPassesReviewOwnershipChecks() {
        long author = user("USER");
        long stranger = user("USER");
        long review = review(place(null), author);

        signIn(author);
        assertThat(guarded.updateReview(review)).isEqualTo("ok");
        assertThat(guarded.deleteReview(review)).isEqualTo("ok");

        signIn(stranger);
        assertThatThrownBy(() -> guarded.updateReview(review)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guarded.deleteReview(review)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void staffGetNoBypassOnOwnContentChecks() {
        // Chốt 2026-10-03: nhân sự xử lý nội dung người khác qua /moderation, không qua endpoint của chủ
        long author = user("USER");
        long admin = user("USER", "MODERATOR", "ADMIN");
        long place = place(null);
        long review = review(place, author);
        long comment = comment(review, author);

        signIn(admin);
        assertThatThrownBy(() -> guarded.updateReview(review)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guarded.deleteComment(comment)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guarded.updatePlace(place)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void authorWithoutThePermissionIsDenied() {
        // RBAC trước: tài khoản nhân sự không có role USER thì không có review:update-own dù là tác giả
        long author = user("MODERATOR");
        long review = review(place(null), author);

        signIn(author);
        assertThatThrownBy(() -> guarded.updateReview(review)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingOrSoftDeletedReviewIs404() {
        long author = user("USER");
        long deleted = review(place(null), author);
        jdbc.update("UPDATE reviews SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);

        signIn(author);
        for (long id : new long[] {deleted, Long.MAX_VALUE}) {
            assertThatThrownBy(() -> guarded.updateReview(id)).isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(e.getCode()).isEqualTo("REVIEW_NOT_FOUND");
            });
        }
    }

    @Test
    void permissionIsCheckedBeforeExistence() {
        // Không có quyền → 403 ngay, không trả 404 (không cần chạm CSDL, không cho dò id)
        long moderatorOnly = user("MODERATOR");

        signIn(moderatorOnly);
        assertThatThrownBy(() -> guarded.updateReview(Long.MAX_VALUE)).isInstanceOf(AccessDeniedException.class);
    }

    // ─── Comment ─────────────────────────────────────────────────────────────

    @Test
    void onlyTheAuthorCanDeleteComment() {
        long reviewer = user("USER");
        long commenter = user("USER");
        long review = review(place(null), reviewer);
        long comment = comment(review, commenter);

        signIn(commenter);
        assertThat(guarded.deleteComment(comment)).isEqualTo("ok");

        // Tác giả review không xóa được bình luận của người khác dưới review của mình
        signIn(reviewer);
        assertThatThrownBy(() -> guarded.deleteComment(comment)).isInstanceOf(AccessDeniedException.class);
    }

    // ─── Place ───────────────────────────────────────────────────────────────

    @Test
    void onlyTheOwnerPassesPlaceChecks() {
        long owner = user("USER", "OWNER");
        long otherOwner = user("USER", "OWNER");
        long place = place(owner);
        long unowned = place(null);

        signIn(owner);
        assertThat(guarded.updatePlace(place)).isEqualTo("ok");
        assertThat(guarded.placeStats(place)).isEqualTo("ok");
        assertThatThrownBy(() -> guarded.updatePlace(unowned)).isInstanceOf(AccessDeniedException.class);

        signIn(otherOwner);
        assertThatThrownBy(() -> guarded.updatePlace(place)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guarded.placeStats(place)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ownerIdWithoutOwnerRoleIsDenied() {
        // owner_id lệch role (vd. dữ liệu sửa tay) → RBAC chặn trước
        long member = user("USER");
        long place = place(member);

        signIn(member);
        assertThatThrownBy(() -> guarded.updatePlace(place)).isInstanceOf(AccessDeniedException.class);
    }

    // ─── Phản hồi review ─────────────────────────────────────────────────────

    @Test
    void onlyTheOwnerOfTheReviewedPlaceCanReply() {
        long owner = user("USER", "OWNER");
        long author = user("USER", "OWNER"); // chủ địa điểm khác, đồng thời là tác giả review
        long review = review(place(owner), author);
        long reviewOnUnownedPlace = review(place(null), author);

        signIn(owner);
        assertThat(guarded.reply(review)).isEqualTo("ok");
        assertThatThrownBy(() -> guarded.reply(reviewOnUnownedPlace)).isInstanceOf(AccessDeniedException.class);

        signIn(author);
        assertThatThrownBy(() -> guarded.reply(review)).isInstanceOf(AccessDeniedException.class);
    }

    // ─── Lỗi lập trình & độ phủ ──────────────────────────────────────────────

    @Test
    void misconfiguredExpressionFailsLoudly() {
        long owner = user("USER", "OWNER");
        long place = place(owner);

        signIn(owner);
        assertThatThrownBy(() -> guarded.wrongTargetType(place))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(OwnershipPermissionEvaluator.PLACE);
        assertThatThrownBy(() -> guarded.notAnOwnershipPermission(place)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyOwnPermissionHasAnOwnershipRule() {
        assertThat(evaluator.targetTypeByPermission().keySet())
                .containsExactlyInAnyOrderElementsOf(
                        Permissions.ALL.stream().filter(p -> p.contains("-own")).collect(Collectors.toSet()));
    }

    // ─── Hỗ trợ ──────────────────────────────────────────────────────────────

    /** Chú thích giống hệt endpoint mục E sẽ dùng. */
    static class Guarded {

        private static final String PREFIX = "hasPermission(";

        @PreAuthorize(PREFIX + "#reviewId, '" + OwnershipPermissionEvaluator.REVIEW + "', '"
                + Permissions.REVIEW_UPDATE_OWN + "')")
        public String updateReview(Long reviewId) {
            return "ok";
        }

        @PreAuthorize(PREFIX + "#reviewId, '" + OwnershipPermissionEvaluator.REVIEW + "', '"
                + Permissions.REVIEW_DELETE_OWN + "')")
        public String deleteReview(Long reviewId) {
            return "ok";
        }

        @PreAuthorize(PREFIX + "#commentId, '" + OwnershipPermissionEvaluator.COMMENT + "', '"
                + Permissions.COMMENT_DELETE_OWN + "')")
        public String deleteComment(Long commentId) {
            return "ok";
        }

        @PreAuthorize(PREFIX + "#placeId, '" + OwnershipPermissionEvaluator.PLACE + "', '"
                + Permissions.PLACE_UPDATE_OWN + "')")
        public String updatePlace(Long placeId) {
            return "ok";
        }

        @PreAuthorize(PREFIX + "#placeId, '" + OwnershipPermissionEvaluator.PLACE + "', '" + Permissions.PLACE_STATS_OWN
                + "')")
        public String placeStats(Long placeId) {
            return "ok";
        }

        @PreAuthorize(PREFIX + "#reviewId, '" + OwnershipPermissionEvaluator.REVIEW + "', '"
                + Permissions.REVIEW_REPLY_OWN_PLACE + "')")
        public String reply(Long reviewId) {
            return "ok";
        }

        @PreAuthorize(PREFIX + "#placeId, '" + OwnershipPermissionEvaluator.REVIEW + "', '"
                + Permissions.PLACE_UPDATE_OWN + "')")
        public String wrongTargetType(Long placeId) {
            return "ok";
        }

        @PreAuthorize(
                PREFIX + "#placeId, '" + OwnershipPermissionEvaluator.PLACE + "', '" + Permissions.PLACE_APPROVE + "')")
        public String notAnOwnershipPermission(Long placeId) {
            return "ok";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class GuardedConfig {
        @Bean
        Guarded guarded() {
            return new Guarded();
        }
    }

    /** Đăng nhập như một request có access token: quyền nạp từ CSDL qua converter thật. */
    private void signIn(long userId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "HS256")
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(converter.convert(jwt));
    }

    private long user(String... roles) {
        String email = "own-" + UUID.randomUUID() + "@localspot.test";
        jdbc.update(
                "INSERT INTO users (email, password_hash, display_name, created_at, updated_at)"
                        + " VALUES (?, 'x', 'Người thử', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                email);
        long id = idOf("SELECT id FROM users WHERE email = ?", email);
        for (String role : roles) {
            jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = ?", id, role);
        }
        return id;
    }

    private long place(Long ownerId) {
        String slug = "own-" + UUID.randomUUID();
        long creator = ownerId != null ? ownerId : user("USER");
        jdbc.update(
                "INSERT INTO places (category_id, created_by, owner_id, name, slug, address, city, location, status,"
                        + " created_at, updated_at)"
                        + " SELECT MIN(id), ?, ?, 'Quán thử', ?, '1 Phố Thử', 'Hà Nội',"
                        + " ST_GeomFromText('POINT(21.0285 105.8542)', 4326), 'APPROVED',"
                        + " UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM categories",
                creator,
                ownerId,
                slug);
        return idOf("SELECT id FROM places WHERE slug = ?", slug);
    }

    private long review(long placeId, long authorId) {
        jdbc.update(
                "INSERT INTO reviews (place_id, user_id, rating, content, visited_at, status, ip_address, ip_prefix,"
                        + " created_at, updated_at) VALUES (?, ?, 4, 'Ngon', CURDATE(), 'PUBLISHED',"
                        + " '127.0.0.1', '127.0.0', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                placeId,
                authorId);
        return idOf("SELECT id FROM reviews WHERE place_id = ? AND user_id = ?", placeId, authorId);
    }

    private long comment(long reviewId, long authorId) {
        jdbc.update(
                "INSERT INTO comments (review_id, user_id, content, status, created_at, updated_at)"
                        + " VALUES (?, ?, 'Đồng ý', 'VISIBLE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                reviewId,
                authorId);
        return idOf("SELECT MAX(id) FROM comments WHERE review_id = ? AND user_id = ?", reviewId, authorId);
    }

    private long idOf(String sql, Object... args) {
        Long id = jdbc.queryForObject(sql, Long.class, args);
        assertThat(id).isNotNull();
        return id;
    }
}
