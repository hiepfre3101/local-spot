package com.localspot.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.localspot.TestcontainersConfiguration;
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
 * Seed demo của profile dev ({@code db/seed/dev}). Profile test mặc định không nạp seed; lớp này bật riêng để kiểm tra
 * seed chạy được trên schema hiện tại, đúng quy mô, và chạy lại không nhân đôi / không lỗi (yêu cầu của script R__).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/seed/dev")
@ActiveProfiles("test")
class DemoSeedTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

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
    void rerunningSeedScriptsChangesNothing() {
        List<Integer> before = snapshot();

        new ResourceDatabasePopulator(
                        new ClassPathResource("db/seed/dev/R__demo_01_users.sql"),
                        new ClassPathResource("db/seed/dev/R__demo_02_places.sql"))
                .execute(dataSource);

        assertThat(snapshot()).isEqualTo(before);
    }

    private List<Integer> snapshot() {
        return List.of(
                count("SELECT COUNT(*) FROM users"),
                count("SELECT COUNT(*) FROM user_roles"),
                count("SELECT COUNT(*) FROM places"),
                count("SELECT COUNT(*) FROM opening_hours"),
                count("SELECT COUNT(*) FROM place_amenity"));
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
