package com.localspot.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localspot.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm chứng migration Flyway trên MySQL 8.4 thật: các ràng buộc lõi (CLAUDE.md §3) được thực thi ở tầng CSDL,
 * không chỉ ở service. Mỗi test chạy trong transaction và rollback nên không để lại dữ liệu.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaMigrationTests {

    private static final String NOW = "2026-09-30 00:00:00.000000";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsAll32TablesFromDesignDocument() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, String.class);

        assertThat(tables)
                .containsExactlyInAnyOrder(
                        "users",
                        "roles",
                        "permissions",
                        "role_permissions",
                        "user_roles",
                        "refresh_tokens",
                        "user_tokens",
                        "user_violations",
                        "categories",
                        "places",
                        "place_photos",
                        "opening_hours",
                        "amenities",
                        "place_amenity",
                        "tags",
                        "taggables",
                        "place_views",
                        "place_claims",
                        "reviews",
                        "review_photos",
                        "review_votes",
                        "comments",
                        "owner_replies",
                        "check_ins",
                        "collections",
                        "collection_place",
                        "follows",
                        "badges",
                        "user_badges",
                        "reports",
                        "notifications",
                        "activity_log");
    }

    @Test
    void usesUtf8mb4AccentInsensitiveCollationOnEveryTable() {
        List<String> others = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_collation <> 'utf8mb4_0900_ai_ci'
                  AND table_name <> 'flyway_schema_history'
                """, String.class);

        assertThat(others).isEmpty();
    }

    @Test
    void placesLocationHasSpatialIndexWithSrid4326() {
        String indexType = jdbc.queryForObject("""
                SELECT index_type FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'places' AND column_name = 'location'
                """, String.class);
        Long srid = jdbc.queryForObject("""
                SELECT srs_id FROM information_schema.st_geometry_columns
                WHERE table_schema = DATABASE() AND table_name = 'places' AND column_name = 'location'
                """, Long.class);

        assertThat(indexType).isEqualTo("SPATIAL");
        assertThat(srid).isEqualTo(4326L);
    }

    @Test
    void storesCoordinatesInLatLngAxisOrder() {
        long userId = insertUser("owner@example.com");
        // Hồ Hoàn Kiếm (21.0288, 105.8525); SRID 4326 trong MySQL 8 hiểu WKT theo thứ tự (lat, lng)
        long placeId = insertPlace(userId, "ho-hoan-kiem", 21.0288, 105.8525);

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT ST_Latitude(location) AS lat, ST_Longitude(location) AS lng,
                       ST_Distance_Sphere(location, ST_GeomFromText('POINT(21.0245 105.8412)', 4326)) AS dist
                FROM places WHERE id = ?
                """, placeId);

        assertThat(((Number) row.get("lat")).doubleValue()).isEqualTo(21.0288);
        assertThat(((Number) row.get("lng")).doubleValue()).isEqualTo(105.8525);
        // Tới Văn Miếu ~1,26 km — sai trục (lng, lat) sẽ ra con số khác hẳn
        assertThat(((Number) row.get("dist")).doubleValue()).isBetween(1_200.0, 1_320.0);
    }

    @Test
    void rejectsSecondReviewOfSamePlaceBySameUserEvenAfterSoftDelete() {
        long ownerId = insertUser("owner@example.com");
        long reviewerId = insertUser("reviewer@example.com");
        long placeId = insertPlace(ownerId, "pho-thin", 21.0, 105.8);
        insertReview(placeId, reviewerId, 5);
        jdbc.update("UPDATE reviews SET deleted_at = ? WHERE user_id = ?", NOW, reviewerId);

        assertThatThrownBy(() -> insertReview(placeId, reviewerId, 4)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void rejectsDuplicateHelpfulVote() {
        long ownerId = insertUser("owner@example.com");
        long reviewerId = insertUser("reviewer@example.com");
        long voterId = insertUser("voter@example.com");
        long reviewId = insertReview(insertPlace(ownerId, "bun-cha", 21.0, 105.8), reviewerId, 4);
        String vote = "INSERT INTO review_votes (review_id, user_id, counted, created_at) VALUES (?, ?, TRUE, ?)";
        jdbc.update(vote, reviewId, voterId, NOW);

        assertThatThrownBy(() -> jdbc.update(vote, reviewId, voterId, NOW)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void enforcesCheckConstraints() {
        long ownerId = insertUser("owner@example.com");
        long reviewerId = insertUser("reviewer@example.com");
        long placeId = insertPlace(ownerId, "com-tam", 21.0, 105.8);

        // MySQL báo vi phạm CHECK bằng mã 3819; bộ dịch lỗi của Spring chưa ánh xạ mã này sang
        // DataIntegrityViolationException (ra UncategorizedSQLException) — xử lý riêng ở exception handler.
        assertThatThrownBy(() -> insertReview(placeId, reviewerId, 6))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_reviews_rating");
        assertThatThrownBy(() -> jdbc.update("UPDATE places SET status = 'DRAFT' WHERE id = ?", placeId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_places_status");
        assertThatThrownBy(() ->
                        jdbc.update("UPDATE places SET price_min = 200000, price_max = 50000 WHERE id = ?", placeId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_places_price_range");
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO follows (follower_id, following_id, created_at) VALUES (?, ?, ?)",
                        ownerId,
                        ownerId,
                        NOW))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_follows_not_self");
    }

    @Test
    void allowsOnlyOneCheckInPerPlacePerDay() {
        long userId = insertUser("owner@example.com");
        long placeId = insertPlace(userId, "cafe-giang", 21.0336, 105.8544);
        String checkIn = """
                INSERT INTO check_ins (user_id, place_id, location, accuracy_m, distance_m, checkin_date, created_at)
                VALUES (?, ?, ST_GeomFromText('POINT(21.0336 105.8544)', 4326), 20, 15, '2026-09-30', ?)
                """;
        jdbc.update(checkIn, userId, placeId, NOW);

        assertThatThrownBy(() -> jdbc.update(checkIn, userId, placeId, NOW)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void refusesToHardDeleteUserWithContent() {
        long userId = insertUser("owner@example.com");
        insertPlace(userId, "quan-nuong", 21.0, 105.8);

        // Người dùng chỉ được xóa mềm + ẩn danh hóa (D7); FK RESTRICT chặn xóa cứng làm mất dữ liệu
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void seedsRolePermissionsWithInheritance() {
        Map<String, List<String>> byRole = Map.of(
                "USER", permissionsOf("USER"),
                "OWNER", permissionsOf("OWNER"),
                "MODERATOR", permissionsOf("MODERATOR"),
                "ADMIN", permissionsOf("ADMIN"));

        assertThat(byRole.get("USER"))
                .containsExactlyInAnyOrder(
                        "review:create", "review:update-own", "review:delete-own", "comment:delete-own");
        assertThat(byRole.get("OWNER"))
                .containsAll(byRole.get("USER"))
                .contains("place:update-own", "place:stats-own", "review:reply-own-place")
                .hasSize(7);
        assertThat(byRole.get("MODERATOR"))
                .containsExactlyInAnyOrder("place:approve", "review:moderate", "report:handle", "claim:approve");
        assertThat(byRole.get("ADMIN"))
                .containsAll(byRole.get("MODERATOR"))
                .contains(
                        "user:view",
                        "user:lock",
                        "user:assign-role",
                        "category:manage",
                        "amenity:manage",
                        "dashboard:view")
                .hasSize(10);
    }

    @Test
    void seedsCategoryTreeAndAmenities() {
        Integer roots = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE parent_id IS NULL", Integer.class);
        Integer orphans = jdbc.queryForObject("""
                SELECT COUNT(*) FROM categories c LEFT JOIN categories p ON p.id = c.parent_id
                WHERE c.parent_id IS NOT NULL AND p.id IS NULL
                """, Integer.class);
        Integer children =
                jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE parent_id IS NOT NULL", Integer.class);
        Integer amenities = jdbc.queryForObject("SELECT COUNT(*) FROM amenities", Integer.class);

        assertThat(roots).isEqualTo(3);
        assertThat(children).isEqualTo(13);
        assertThat(orphans).isZero();
        assertThat(amenities).isEqualTo(14);
    }

    private List<String> permissionsOf(String role) {
        return jdbc.queryForList("""
                SELECT p.name FROM permissions p
                JOIN role_permissions rp ON rp.permission_id = p.id
                JOIN roles r ON r.id = rp.role_id
                WHERE r.name = ?
                """, String.class, role);
    }

    private long insertUser(String email) {
        jdbc.update("""
                INSERT INTO users (email, password_hash, display_name, created_at, updated_at)
                VALUES (?, '$2a$10$hash', 'Test', ?, ?)
                """, email, NOW, NOW);
        return lastId();
    }

    private long insertPlace(long createdBy, String slug, double lat, double lng) {
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES ((SELECT id FROM categories WHERE slug = 'quan-an'), ?, ?, ?, '1 Hàng Bài', 'Hà Nội',
                        ST_PointFromText(CONCAT('POINT(', ?, ' ', ?, ')'), 4326), 'APPROVED', ?, ?)
                """, createdBy, slug, slug, lat, lng, NOW, NOW);
        return lastId();
    }

    private long insertReview(long placeId, long userId, int rating) {
        jdbc.update("""
                INSERT INTO reviews (place_id, user_id, rating, content, visited_at, status, ip_address, ip_prefix,
                                     created_at, updated_at)
                VALUES (?, ?, ?, 'Nội dung đánh giá đủ dài để hợp lệ.', '2026-09-29', 'PUBLISHED',
                        '113.22.5.17', '113.22.5.0', ?, ?)
                """, placeId, userId, rating, NOW, NOW);
        return lastId();
    }

    private long lastId() {
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        assertThat(id).isNotNull();
        return id;
    }
}
