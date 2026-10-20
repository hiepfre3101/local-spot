package com.localspot.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.localspot.TestcontainersConfiguration;
import com.localspot.entity.User;
import com.localspot.listener.DemoDataBootstrap;
import com.localspot.repository.UserRepository;
import com.localspot.service.ReviewPhotoService;
import com.localspot.service.TrustScoreService;
import com.localspot.storage.InMemoryObjectStorage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Seed demo của profile dev ({@code db/seed/dev}) + {@link DemoDataBootstrap}. Profile test mặc định không nạp seed; lớp
 * này bật riêng để kiểm tra seed chạy được trên schema hiện tại, đúng quy mô, tôn trọng các ràng buộc nghiệp vụ mà service
 * thật bảo đảm (cổng viết review, bộ đếm, counted, điểm, độ sâu bình luận), và chạy lại không nhân đôi / không lỗi.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/seed/dev",
            "localspot.demo.bootstrap=true"
        })
@ActiveProfiles("test")
class DemoSeedTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DemoDataBootstrap bootstrap;

    @Autowired
    private ReviewPhotoService reviewPhotos;

    @Autowired
    private InMemoryObjectStorage storage;

    @Autowired
    private UserRepository users;

    @Autowired
    private TrustScoreService trust;

    @Test
    void seedsSixtyAccountsWithExpectedRoles() {
        assertThat(count("SELECT COUNT(*) FROM users WHERE email LIKE '%@localspot.test'"))
                .isEqualTo(60);
        assertThat(usersWithRole("ADMIN")).containsExactly("admin@localspot.test");
        assertThat(usersWithRole("MODERATOR")).containsExactlyInAnyOrder("mod1@localspot.test", "mod2@localspot.test");
        assertThat(usersWithRole("OWNER")).hasSize(6);
        assertThat(usersWithRole("USER")).hasSize(60);
        assertThat(count("SELECT COUNT(*) FROM users WHERE email LIKE 'member%' AND email_verified_at IS NULL"))
                .isEqualTo(5);
    }

    @Test
    void demoPasswordMatchesSpringBcrypt() {
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'admin@localspot.test'", String.class);

        assertThat(new BCryptPasswordEncoder().matches("LocalSpot2026", hash)).isTrue();
    }

    @Test
    void seedsThreeHundredPlacesAcrossFiveCities() {
        Map<String, Integer> byCity = countBy("SELECT city AS k, COUNT(*) AS v FROM places GROUP BY city");
        Map<String, Integer> byStatus = countBy("SELECT status AS k, COUNT(*) AS v FROM places GROUP BY status");

        assertThat(byCity)
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("Hà Nội", 110, "TP. Hồ Chí Minh", 100, "Đà Nẵng", 40, "Huế", 25, "Đà Lạt", 25));
        assertThat(byStatus).containsOnlyKeys("APPROVED", "PENDING", "REJECTED");
        assertThat(byStatus.get("PENDING")).isEqualTo(15);
        assertThat(count("SELECT COUNT(*) FROM places WHERE owner_id IS NOT NULL"))
                .isEqualTo(12);
        assertThat(count("SELECT COUNT(*) FROM places WHERE status <> 'PENDING' AND moderated_by IS NULL"))
                .isZero();
    }

    @Test
    void placesLieNearTheirCityCentre() {
        // Tâm Hà Nội / TP.HCM; rải ±spread/2 độ quanh tâm nên luôn < 7 km — sai trục (lat, lng) sẽ lệch hàng nghìn km
        Integer farFromHanoi = count("""
                SELECT COUNT(*) FROM places WHERE city = 'Hà Nội'
                  AND ST_Distance_Sphere(location, ST_GeomFromText('POINT(21.0285 105.8542)', 4326)) > 7000
                """);
        Integer farFromSaigon = count("""
                SELECT COUNT(*) FROM places WHERE city = 'TP. Hồ Chí Minh'
                  AND ST_Distance_Sphere(location, ST_GeomFromText('POINT(10.7769 106.7009)', 4326)) > 7000
                """);

        assertThat(farFromHanoi).isZero();
        assertThat(farFromSaigon).isZero();
    }

    @Test
    void seedsOpeningHoursAndAmenitiesByCategory() {
        assertThat(count("""
                        SELECT COUNT(*) FROM opening_hours oh
                        JOIN places p ON p.id = oh.place_id JOIN categories c ON c.id = p.category_id
                        WHERE c.slug = 'di-tich-bao-tang' AND oh.day_of_week = 1
                        """)).isZero();
        assertThat(count("SELECT COUNT(*) FROM opening_hours WHERE close_time < open_time"))
                .isPositive();
        assertThat(count("""
                        SELECT COUNT(*) FROM places p WHERE NOT EXISTS
                          (SELECT 1 FROM opening_hours oh WHERE oh.place_id = p.id)
                        """)).isZero();
        assertThat(count("""
                        SELECT COUNT(*) FROM place_amenity pa
                        JOIN amenities a ON a.id = pa.amenity_id
                        JOIN places p ON p.id = pa.place_id JOIN categories c ON c.id = p.category_id
                        WHERE a.slug = 've-vao-cua' AND c.slug <> 'di-tich-bao-tang'
                        """)).isZero();
        assertThat(count("SELECT COUNT(*) FROM place_amenity")).isGreaterThan(300);
    }

    @Test
    void seedsAboutTwoThousandReviewsWithStatusMix() {
        Map<String, Integer> byStatus = countBy("SELECT status AS k, COUNT(*) AS v FROM reviews GROUP BY status");
        int total = byStatus.values().stream().mapToInt(Integer::intValue).sum();

        assertThat(total).isBetween(1800, 2400);
        assertThat(byStatus).containsOnlyKeys("PUBLISHED", "PENDING", "REJECTED");
        assertThat(byStatus.get("PENDING")).isBetween(total * 4 / 100, total * 10 / 100);
        assertThat(byStatus.get("REJECTED")).isBetween(total / 100, total * 5 / 100);
        // Hàng chờ UC28 trông như vừa gửi: mọi review chờ đều trong 10 ngày trước mốc seed
        assertThat(count("""
                        SELECT COUNT(*) FROM reviews
                        WHERE status = 'PENDING' AND created_at < TIMESTAMP('2026-09-30') - INTERVAL 11 DAY
                        """)).isZero();
    }

    @Test
    void reviewsPassTheSameGatesAsTheWriteApi() {
        // Cổng UC12: email đã xác thực, địa điểm đã duyệt, chủ không tự đánh giá, ≥ 20 ký tự, ngày đến ≤ ngày viết
        assertThat(count("""
                        SELECT COUNT(*) FROM reviews r JOIN users u ON u.id = r.user_id
                        WHERE u.email_verified_at IS NULL
                        """)).isZero();
        assertThat(count("""
                        SELECT COUNT(*) FROM reviews r JOIN places p ON p.id = r.place_id
                        WHERE p.status <> 'APPROVED' OR p.owner_id = r.user_id
                        """)).isZero();
        assertThat(count("SELECT COUNT(*) FROM reviews WHERE CHAR_LENGTH(TRIM(content)) < 20"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM reviews WHERE visited_at > DATE(created_at)"))
                .isZero();
        assertThat(count("""
                        SELECT COUNT(*) FROM reviews r
                        JOIN users u ON u.id = r.user_id JOIN places p ON p.id = r.place_id
                        WHERE r.created_at < u.created_at OR r.created_at < p.moderated_at
                        """)).isZero();
        // Nhân sự không viết review demo; review đã duyệt / từ chối có người duyệt, review chờ thì chưa
        assertThat(count("""
                        SELECT COUNT(*) FROM reviews r JOIN users u ON u.id = r.user_id
                        WHERE u.email NOT REGEXP '^(member|owner)'
                        """)).isZero();
        assertThat(count("""
                        SELECT COUNT(*) FROM reviews
                        WHERE (status = 'PENDING' AND moderated_by IS NOT NULL)
                           OR (status = 'REJECTED' AND (moderated_by IS NULL OR reject_reason IS NULL))
                        """)).isZero();
    }

    @Test
    void popularityIsSkewedSoBayesianRankingHasSomethingToShow() {
        List<Integer> perPlace = jdbc.queryForList("""
                SELECT COUNT(r.id) FROM places p
                LEFT JOIN reviews r ON r.place_id = p.id AND r.status = 'PUBLISHED'
                WHERE p.status = 'APPROVED' GROUP BY p.id
                """, Integer.class);

        assertThat(perPlace).contains(0);
        assertThat(perPlace.stream().filter(c -> c >= 25)).hasSizeGreaterThan(10);
        // Cặp minh họa plan §5: địa điểm ít review có trung bình thô cao hơn nhưng điểm Bayesian thấp hơn
        assertThat(count("""
                        SELECT COUNT(*) FROM places few JOIN places many
                        WHERE few.review_count BETWEEN 1 AND 3 AND many.review_count >= 25
                          AND few.avg_rating > many.avg_rating AND few.bayesian_score < many.bayesian_score
                        """)).isPositive();
    }

    @Test
    void startupRecalculatesEveryPlaceFromPublishedReviews() {
        assertThat(count("""
                        SELECT COUNT(*) FROM places p WHERE p.review_count <> (
                          SELECT COUNT(*) FROM reviews r WHERE r.place_id = p.id AND r.status = 'PUBLISHED'
                            AND r.deleted_at IS NULL)
                        """)).isZero();
        assertThat(count("""
                        SELECT COUNT(*) FROM places
                        WHERE (review_count = 0 AND bayesian_score <> 0) OR (review_count > 0 AND bayesian_score = 0)
                        """)).isZero();
    }

    @Test
    void voteCountersMatchARecountAndOnlyVeteransAreCounted() {
        assertThat(count("""
                        SELECT COUNT(*) FROM reviews r
                        WHERE r.helpful_count <> (SELECT COUNT(*) FROM review_votes v WHERE v.review_id = r.id)
                        """)).isZero();
        assertThat(count("""
                        SELECT COUNT(*) FROM users u WHERE u.helpful_votes_count <> (
                          SELECT COUNT(*) FROM review_votes v JOIN reviews r ON r.id = v.review_id
                          WHERE r.user_id = u.id AND v.counted)
                        """)).isZero();
        assertThat(count("""
                        SELECT COUNT(*) FROM review_votes v
                        JOIN reviews r ON r.id = v.review_id JOIN users voter ON voter.id = v.user_id
                        WHERE v.user_id = r.user_id OR r.status <> 'PUBLISHED' OR v.created_at < r.created_at
                           OR v.counted <> (voter.email LIKE 'member%'
                                            AND voter.created_at <= TIMESTAMP('2026-09-30') - INTERVAL 250 DAY)
                        """)).isZero();
        assertThat(count("SELECT COUNT(*) FROM review_votes WHERE counted")).isPositive();
        assertThat(count("SELECT COUNT(*) FROM review_votes WHERE NOT counted")).isPositive();
    }

    @Test
    void trustSpreadsAroundThePublishThreshold() {
        List<User> verified = users.findAll().stream()
                .filter(u -> u.getEmail().matches("^(member|owner).*@localspot\\.test$"))
                .filter(User::isEmailVerified)
                .toList();
        Map<Long, Integer> scores = trust.trustScoresOf(verified);

        // Có người đăng thẳng (≥ 30) và có người đã xác thực vẫn vào hàng chờ — cả hai nhánh FR-23 đều demo được
        assertThat(scores.values().stream().filter(s -> s >= 30)).hasSizeGreaterThan(20);
        assertThat(scores.values().stream().filter(s -> s < 30)).hasSizeGreaterThanOrEqualTo(5);
        // Người mới (< 60 ngày): ít review, tất cả đang chờ duyệt
        assertThat(count("""
                        SELECT COUNT(*) FROM reviews r JOIN users u ON u.id = r.user_id
                        WHERE u.email LIKE 'member%' AND u.created_at > TIMESTAMP('2026-09-30') - INTERVAL 60 DAY
                          AND r.status <> 'PENDING'
                        """)).isZero();
        // Vi phạm của review bị từ chối đều đã hết hạn → không kéo trust hiện tại
        assertThat(count("""
                        SELECT COUNT(*) FROM reviews r WHERE r.status = 'REJECTED' AND NOT EXISTS (
                          SELECT 1 FROM user_violations v WHERE v.source_type = 'REVIEW_REJECTED' AND v.source_id = r.id
                            AND v.user_id = r.user_id AND v.points = 25 AND v.expires_at < NOW())
                        """)).isZero();
    }

    @Test
    void commentsAreTwoLevelsOnVisibleReviews() {
        assertThat(count("SELECT COUNT(*) FROM comments WHERE parent_id IS NULL"))
                .isGreaterThan(200);
        assertThat(count("SELECT COUNT(*) FROM comments WHERE parent_id IS NOT NULL"))
                .isPositive();
        assertThat(count("""
                        SELECT COUNT(*) FROM comments reply JOIN comments root ON root.id = reply.parent_id
                        JOIN reviews r ON r.id = reply.review_id
                        WHERE root.parent_id IS NOT NULL OR root.review_id <> reply.review_id
                           OR reply.user_id <> r.user_id OR reply.created_at < root.created_at
                        """)).isZero();
        assertThat(count("""
                        SELECT COUNT(*) FROM comments c
                        JOIN reviews r ON r.id = c.review_id JOIN users u ON u.id = c.user_id
                        WHERE r.status <> 'PUBLISHED' OR u.email_verified_at IS NULL OR c.created_at < r.created_at
                        """)).isZero();
    }

    @Test
    void startupAttachesPlaceholderPhotosThroughThePipeline() {
        List<Map<String, Object>> photos = jdbc.queryForList("""
                SELECT p.id, p.storage_key, r.rating, r.status FROM review_photos p
                JOIN reviews r ON r.id = p.review_id
                """);

        assertThat(photos).hasSizeGreaterThan(100);
        assertThat(photos).allSatisfy(p -> {
            assertThat((Integer) p.get("rating")).isGreaterThanOrEqualTo(4);
            assertThat(p.get("status")).isEqualTo("PUBLISHED");
        });
        // Consumer không chạy ở profile test → ảnh còn ở incoming/; xử lý một ảnh như consumer để chắc ảnh minh họa hợp
        // lệ
        Map<String, Object> first = photos.get(0);
        String key = (String) first.get("storage_key");
        assertThat(storage.contains("incoming/" + key)).isTrue();

        reviewPhotos.process(((Number) first.get("id")).longValue());

        assertThat(jdbc.queryForObject("SELECT status FROM review_photos WHERE id = ?", String.class, first.get("id")))
                .isEqualTo("READY");
        assertThat(storage.keysStartingWith(key)).hasSize(3);
    }

    @Test
    void checkInsPassTheCheckInRulesAndLabelReviews() {
        assertThat(count("SELECT COUNT(*) FROM check_ins")).isGreaterThan(500);
        // U5 tính lại bằng chính hàm của CheckInService, không tin cột distance_m của seed
        assertThat(count("""
                        SELECT COUNT(*) FROM check_ins c JOIN places p ON p.id = c.place_id
                        WHERE ST_Distance_Sphere(c.location, p.location) > 200 OR c.accuracy_m > 100
                           OR ABS(ST_Distance_Sphere(c.location, p.location) - c.distance_m) > 1
                           OR p.status <> 'APPROVED' OR p.owner_id = c.user_id
                        """)).isZero();
        assertThat(count("""
                        SELECT COUNT(*) FROM places p
                        WHERE p.checkin_count <> (SELECT COUNT(*) FROM check_ins c WHERE c.place_id = p.id)
                        """)).isZero();
        // Mọi check-in demo rơi vào ngày đến ≤ ngày viết → đều gắn nhãn cho review tương ứng
        assertThat(count("""
                        SELECT COUNT(*) FROM check_ins c
                        JOIN reviews r ON r.user_id = c.user_id AND r.place_id = c.place_id
                        WHERE c.checkin_date > DATE(CONVERT_TZ(r.created_at, '+00:00', '+07:00'))
                        """)).isZero();
    }

    @Test
    void collectionsFollowTheCollectionRules() {
        assertThat(count("SELECT COUNT(*) FROM collections WHERE is_public")).isGreaterThan(5);
        assertThat(count("SELECT COUNT(*) FROM collections WHERE NOT is_public"))
                .isGreaterThan(5);
        // Công khai ⇔ có slug; slug cùng dạng CollectionService sinh; chỉ địa điểm đã duyệt; trần 50 / 100
        assertThat(count("""
                        SELECT COUNT(*) FROM collections
                        WHERE is_public <> (share_slug IS NOT NULL)
                           OR (share_slug IS NOT NULL AND share_slug NOT REGEXP '^[a-z0-9-]+-[a-z0-9]{6}$')
                        """)).isZero();
        assertThat(count("""
                        SELECT COUNT(*) FROM collection_place cp JOIN places p ON p.id = cp.place_id
                        WHERE p.status <> 'APPROVED'
                        """)).isZero();
        assertThat(count("""
                        SELECT COUNT(*) FROM (SELECT collection_id FROM collection_place
                                              GROUP BY collection_id HAVING COUNT(*) NOT BETWEEN 3 AND 100) x
                        """)).isZero();
        assertThat(count("""
                        SELECT COUNT(*) FROM (SELECT user_id FROM collections GROUP BY user_id HAVING COUNT(*) > 50) x
                        """)).isZero();
    }

    @Test
    void startupComputesContributionPointsAndBadges() {
        // Mỗi người viết review demo có điểm; huy hiệu cấp 2+ có người đạt (thành viên ~38 review)
        assertThat(count("""
                        SELECT COUNT(*) FROM users u
                        WHERE EXISTS (SELECT 1 FROM reviews r WHERE r.user_id = u.id AND r.status = 'PUBLISHED')
                          AND u.contribution_points = 0
                        """)).isZero();
        assertThat(count("""
                        SELECT COUNT(DISTINCT ub.user_id) FROM user_badges ub JOIN badges b ON b.id = ub.badge_id
                        WHERE b.code = 'REVIEWER_3'
                        """)).isPositive();
        assertThat(count("SELECT COUNT(DISTINCT badge_id) FROM user_badges")).isGreaterThanOrEqualTo(4);
        // Người mới (chỉ có review chờ duyệt) chưa có huy hiệu người đánh giá
        assertThat(count("""
                        SELECT COUNT(*) FROM user_badges ub JOIN users u ON u.id = ub.user_id
                        JOIN badges b ON b.id = ub.badge_id
                        WHERE b.code LIKE 'REVIEWER%' AND u.email LIKE 'member%'
                          AND u.created_at > TIMESTAMP('2026-09-30') - INTERVAL 60 DAY
                        """)).isZero();
    }

    @Test
    void rerunningSeedScriptsChangesNothing() {
        List<Integer> before = snapshot();

        new ResourceDatabasePopulator(
                        new ClassPathResource("db/seed/dev/R__demo_01_users.sql"),
                        new ClassPathResource("db/seed/dev/R__demo_02_places.sql"),
                        new ClassPathResource("db/seed/dev/R__demo_03_reviews.sql"),
                        new ClassPathResource("db/seed/dev/R__demo_04_checkins.sql"),
                        new ClassPathResource("db/seed/dev/R__demo_05_collections.sql"))
                .execute(dataSource);
        bootstrap.recalculateRatings();
        bootstrap.refreshContributions();
        bootstrap.attachReviewPhotos();

        assertThat(snapshot()).isEqualTo(before);
    }

    private List<Integer> snapshot() {
        return List.of(
                count("SELECT COUNT(*) FROM users"),
                count("SELECT COUNT(*) FROM user_roles"),
                count("SELECT COUNT(*) FROM places"),
                count("SELECT COUNT(*) FROM opening_hours"),
                count("SELECT COUNT(*) FROM place_amenity"),
                count("SELECT COUNT(*) FROM reviews"),
                count("SELECT COUNT(*) FROM review_votes"),
                count("SELECT COALESCE(SUM(helpful_count), 0) FROM reviews"),
                count("SELECT COALESCE(SUM(helpful_votes_count), 0) FROM users"),
                count("SELECT COUNT(*) FROM comments"),
                count("SELECT COUNT(*) FROM user_violations"),
                count("SELECT COUNT(*) FROM review_photos"),
                count("SELECT COUNT(*) FROM check_ins"),
                count("SELECT COUNT(*) FROM collections"),
                count("SELECT COUNT(*) FROM user_badges"),
                count("SELECT COALESCE(SUM(contribution_points), 0) FROM users"),
                count("SELECT COUNT(*) FROM collection_place"),
                count("SELECT COALESCE(SUM(checkin_count), 0) FROM places"),
                count("SELECT COALESCE(SUM(review_count), 0) FROM places"),
                count("SELECT CAST(COALESCE(SUM(bayesian_score), 0) * 1000 AS SIGNED) FROM places"));
    }

    private List<String> usersWithRole(String role) {
        return jdbc.queryForList("""
                SELECT u.email FROM users u
                JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id
                WHERE r.name = ?
                """, String.class, role);
    }

    private Map<String, Integer> countBy(String sql) {
        Map<String, Integer> result = new HashMap<>();
        jdbc.query(sql, rs -> {
            result.put(rs.getString("k"), rs.getInt("v"));
        });
        return result;
    }

    private Integer count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
