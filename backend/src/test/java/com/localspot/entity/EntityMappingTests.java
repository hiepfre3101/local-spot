package com.localspot.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.localspot.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm chứng mapping chạy thật trên MySQL 8.4 (ngoài {@code ddl-auto: validate} vốn chỉ so kiểu cột): ghi qua
 * Hibernate rồi đọc lại bằng SQL thuần để bắt các lỗi âm thầm như sai trục tọa độ, lệch thứ ngày, JSON sai định dạng.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityMappingTests {

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void writesPlaceLocationInLatLngAxisOrderForSrid4326() {
        Place place = persistPlace("pho-thin", 21.0288, 105.8525);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT ST_SRID(location) AS srid, ST_Latitude(location) AS lat, ST_Longitude(location) AS lng"
                        + " FROM places WHERE id = ?",
                place.getId());
        assertThat(((Number) row.get("srid")).intValue()).isEqualTo(GeoPoints.WGS84);
        assertThat(((Number) row.get("lat")).doubleValue()).isCloseTo(21.0288, within(1e-9));
        assertThat(((Number) row.get("lng")).doubleValue()).isCloseTo(105.8525, within(1e-9));

        em.clear();
        Place reloaded = em.find(Place.class, place.getId());
        assertThat(GeoPoints.lat(reloaded.getLocation())).isCloseTo(21.0288, within(1e-9));
        assertThat(GeoPoints.lng(reloaded.getLocation())).isCloseTo(105.8525, within(1e-9));
    }

    @Test
    void fillsAuditTimestampsAndDerivedDefaults() {
        Place place = persistPlace("bun-cha", 21.0, 105.8);

        assertThat(place.getCreatedAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
        assertThat(place.getUpdatedAt()).isNotNull();
        assertThat(place.getBayesianScore()).isZero();
        assertThat(place.getVersion()).isZero();
    }

    @Test
    void storesOpeningHoursAsIsoDayNumbers() {
        Place place = persistPlace("cafe-dem", 21.0, 105.8);
        place.addOpeningHour(new OpeningHour(DayOfWeek.MONDAY, LocalTime.of(7, 0), LocalTime.of(11, 0)));
        place.addOpeningHour(new OpeningHour(DayOfWeek.SUNDAY, LocalTime.of(18, 0), LocalTime.of(2, 0)));
        em.flush();

        List<Integer> days = jdbc.queryForList(
                "SELECT day_of_week FROM opening_hours WHERE place_id = ? ORDER BY day_of_week",
                Integer.class,
                place.getId());
        assertThat(days).containsExactly(1, 7);

        em.clear();
        List<OpeningHour> hours = em.find(Place.class, place.getId()).getOpeningHours();
        assertThat(hours).extracting(OpeningHour::getDayOfWeek).containsExactly(DayOfWeek.MONDAY, DayOfWeek.SUNDAY);
        assertThat(hours.get(1).isOvernight()).isTrue();
    }

    @Test
    void hidesSoftDeletedRowsFromQueries() {
        Place visible = persistPlace("con-mo", 21.0, 105.8);
        Place deleted = persistPlace("da-xoa", 21.0, 105.8);
        deleted.setDeletedAt(Instant.now());
        em.flush();
        em.clear();

        List<String> slugs = em.createQuery(
                        "SELECT p.slug FROM Place p WHERE p.slug IN ('con-mo', 'da-xoa')", String.class)
                .getResultList();
        assertThat(slugs).containsExactly(visible.getSlug());
        // Dữ liệu vẫn còn trong bảng — xóa mềm, không mất bản ghi
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM places WHERE slug = 'da-xoa'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void roundTripsJsonColumns() {
        User user = persistUser("claimer@example.com");
        Place place = persistPlace("quan-cua-toi", 21.0, 105.8);
        PlaceClaim claim = new PlaceClaim(place, user, "0901234567", null, List.of("claims/1/a.pdf", "claims/1/b.jpg"));
        em.persist(new Notification(user, "PLACE_APPROVED", Map.of("placeId", 7, "placeName", "Phở Thìn")));
        em.persist(claim);
        em.flush();

        String raw = jdbc.queryForObject(
                "SELECT JSON_EXTRACT(evidence_keys, '$[1]') FROM place_claims WHERE id = ?",
                String.class,
                claim.getId());
        assertThat(raw).isEqualTo("\"claims/1/b.jpg\"");

        em.clear();
        assertThat(em.find(PlaceClaim.class, claim.getId()).getEvidenceKeys())
                .containsExactly("claims/1/a.pdf", "claims/1/b.jpg");
        Notification notification = em.createQuery(
                        "SELECT n FROM Notification n WHERE n.user.id = :u", Notification.class)
                .setParameter("u", user.getId())
                .getSingleResult();
        assertThat(notification.getData()).containsEntry("placeName", "Phở Thìn");
    }

    @Test
    void mapsCompositeKeysAndRoles() {
        User author = persistUser("author@example.com");
        User voter = persistUser("voter@example.com");
        Place place = persistPlace("com-tam", 21.0, 105.8);
        Review review = new Review(
                place,
                author,
                5,
                "Cơm ngon, phục vụ nhanh, giá hợp lý.",
                LocalDate.of(2026, 9, 29),
                ReviewStatus.PUBLISHED,
                "113.22.5.17",
                "113.22.5.0");
        em.persist(review);
        em.flush();
        em.persist(new ReviewVote(review, voter, true));
        em.persist(new Follow(voter, author));
        voter.getRoles()
                .add(em.createQuery("SELECT r FROM Role r WHERE r.name = :n", Role.class)
                        .setParameter("n", Role.USER)
                        .getSingleResult());
        em.flush();
        em.clear();

        ReviewVote vote = em.find(ReviewVote.class, new ReviewVote.Id(review.getId(), voter.getId()));
        assertThat(vote.isCounted()).isTrue();
        assertThat(em.find(Follow.class, new Follow.Id(voter.getId(), author.getId())))
                .isNotNull();
        User reloaded = em.find(User.class, voter.getId());
        assertThat(reloaded.getRoles()).extracting(Role::getName).containsExactly(Role.USER);
        assertThat(reloaded.getRoles().iterator().next().getPermissions())
                .extracting(Permission::getName)
                .contains("review:create");
    }

    private User persistUser(String email) {
        User user = new User(email, "$2a$10$hash", "Người dùng thử");
        em.persist(user);
        return user;
    }

    private Place persistPlace(String slug, double lat, double lng) {
        User creator = persistUser(slug + "@example.com");
        Category category = em.createQuery("SELECT c FROM Category c WHERE c.slug = 'quan-an'", Category.class)
                .getSingleResult();
        Place place = new Place(
                category, creator, slug, slug, "1 Hàng Bài", "Hà Nội", GeoPoints.of(lat, lng), PlaceStatus.APPROVED);
        em.persist(place);
        em.flush();
        return place;
    }
}
