package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.localspot.entity.Place;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Check-in (UC16, FR-24, U5) qua HTTP trên MySQL thật: bán kính 200 m tính bằng {@code ST_Distance_Sphere}, GPS kém hơn
 * 100 m bị từ chối, 1 lần / địa điểm / ngày kể cả khi request chen nhau, cổng email / chủ / địa điểm đã duyệt, bộ đếm
 * {@code checkin_count}, lịch sử chỉ của chính mình, nhãn "đã check-in" trên review.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckInFlowTests {

    private static final String PASSWORD = "LocalSpot2026";

    /** Tâm địa điểm thử. MySQL dùng bán kính Trái Đất 6 370 986 m → 1° vĩ độ ≈ 111 195 m. */
    private static final double LAT = 21.03;

    private static final double LNG = 105.85;
    private static final double METERS_PER_DEGREE_LAT = 6_370_986 * Math.PI / 180;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void checkInWithinRadiusCountsOncePerDay() throws Exception {
        Account member = account(true);
        long placeId = place("APPROVED", null);

        checkIn(member, placeId, north(100), 15)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeId").value(placeId))
                .andExpect(jsonPath("$.place.id").value(placeId))
                .andExpect(jsonPath("$.distanceM").value(100))
                .andExpect(jsonPath("$.checkinDate").isNotEmpty())
                .andExpect(jsonPath("$.lat").doesNotExist());
        assertThat(checkinCount(placeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT accuracy_m FROM check_ins WHERE user_id = ?", Integer.class, member.id()))
                .isEqualTo(15);

        checkIn(member, placeId, north(20), 5)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECKIN_ALREADY_TODAY"));
        assertThat(checkinCount(placeId)).isEqualTo(1);
        // Người khác cùng ngày vẫn check-in được — giới hạn theo (người, địa điểm, ngày)
        checkIn(account(true), placeId, north(20), 5).andExpect(status().isCreated());
        assertThat(checkinCount(placeId)).isEqualTo(2);
        String slug = jdbc.queryForObject("SELECT slug FROM places WHERE id = ?", String.class, placeId);
        mvc.perform(get("/api/v1/places/" + slug))
                .andExpect(jsonPath("$.checkinCount").value(2))
                .andExpect(jsonPath("$.checkedInToday").value(false)); // khách
    }

    @Test
    void detailTellsTheViewerWhetherTheyCheckedInToday() throws Exception {
        Account member = account(true);
        long placeId = place("APPROVED", null);
        String slug = jdbc.queryForObject("SELECT slug FROM places WHERE id = ?", String.class, placeId);

        perform(member, get("/api/v1/places/" + slug))
                .andExpect(jsonPath("$.checkedInToday").value(false));
        checkIn(member, placeId, north(50), 10).andExpect(status().isCreated());
        perform(member, get("/api/v1/places/" + slug))
                .andExpect(jsonPath("$.checkedInToday").value(true));
        // Người khác chưa check-in; check-in hôm qua không tính là hôm nay
        perform(account(true), get("/api/v1/places/" + slug))
                .andExpect(jsonPath("$.checkedInToday").value(false));
        jdbc.update("UPDATE check_ins SET checkin_date = checkin_date - INTERVAL 1 DAY WHERE user_id = ?", member.id());
        perform(member, get("/api/v1/places/" + slug))
                .andExpect(jsonPath("$.checkedInToday").value(false));
    }

    @Test
    void radiusBoundaryIsTwoHundredMetres() throws Exception {
        Account member = account(true);
        long inside = place("APPROVED", null);
        long outside = place("APPROVED", null);

        checkIn(member, inside, north(199), 10).andExpect(status().isCreated());
        MvcResult tooFar = checkIn(member, outside, north(201), 10)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CHECKIN_TOO_FAR"))
                .andReturn();
        // Làm tròn lên, sai số dấu phẩy động quanh 201 m — không bao giờ hiện "200 m" cho một lần bị từ chối
        assertThat((Integer) JsonPath.read(tooFar.getResponse().getContentAsString(), "$.distanceM"))
                .isBetween(201, 202);
        MvcResult farAway = checkIn(member, outside, north(5000), 10)
                .andExpect(status().isUnprocessableContent())
                .andReturn();
        assertThat((Integer) JsonPath.read(farAway.getResponse().getContentAsString(), "$.distanceM"))
                .isBetween(5000, 5001);
        assertThat(checkinCount(outside)).isZero();
        assertThat(rows(member)).isEqualTo(1);
    }

    @Test
    void inaccurateGpsIsRejectedBeforeAnythingElse() throws Exception {
        Account member = account(true);
        long placeId = place("APPROVED", null);

        checkIn(member, placeId, north(10), 100.5)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("GPS_INACCURATE"))
                .andExpect(jsonPath("$.errors[0].field").value("accuracyM"));
        // Sai số kém kèm vị trí xa → báo GPS trước (gợi ý đúng việc cần làm: bật GPS, không phải đi lại gần)
        checkIn(member, placeId, north(5000), 500).andExpect(jsonPath("$.code").value("GPS_INACCURATE"));
        checkIn(member, placeId, north(10), 100).andExpect(status().isCreated()); // đúng 100 m vẫn nhận
    }

    @Test
    void gatesMatchTheReviewRules() throws Exception {
        Account verified = account(true);
        long approved = place("APPROVED", null);

        checkIn(account(false), approved, north(10), 10)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        checkIn(verified, place("APPROVED", verified.id()), north(10), 10)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));
        for (String status : List.of("PENDING", "REJECTED", "HIDDEN")) {
            checkIn(verified, place(status, null), north(10), 10)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
        }
        checkIn(verified, Long.MAX_VALUE, north(10), 10).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/places/" + approved + "/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(LAT, LNG, 10)))
                .andExpect(status().isUnauthorized());
        perform(
                        verified,
                        post("/api/v1/places/" + approved + "/check-in")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"lat\":91,\"lng\":105.85}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(rows(verified)).isZero();
    }

    @Test
    void concurrentTapsCreateOneCheckIn() throws Exception {
        Account member = account(true);
        long placeId = place("APPROVED", null);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<MvcResult>> taps = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                taps.add(() -> checkIn(member, placeId, north(30), 10).andReturn());
            }
            List<Integer> statuses = new ArrayList<>();
            List<String> bodies = new ArrayList<>();
            for (Future<MvcResult> f : pool.invokeAll(taps)) {
                statuses.add(f.get().getResponse().getStatus());
                bodies.add(f.get().getResponse().getContentAsString());
            }

            assertThat(statuses).containsOnlyOnce(201).containsOnly(201, 409);
            // Khóa dòng người dùng → 409 có mã riêng, không phải lỗi UNIQUE chung chung của CSDL
            assertThat(bodies.stream().filter(b -> b.contains("DUPLICATE_RESOURCE")))
                    .isEmpty();
        } finally {
            pool.shutdownNow();
        }
        assertThat(checkinCount(placeId)).isEqualTo(1);
    }

    @Test
    void editingThePlaceThroughTheEntityKeepsTheCounter() throws Exception {
        Account member = account(true);
        long placeId = place("APPROVED", null);

        tx.executeWithoutResult(status -> {
            Place place = entityManager.find(Place.class, placeId); // nạp khi checkin_count = 0
            try {
                checkIn(member, placeId, north(10), 10).andExpect(status().isCreated());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            place.setDescription("Mô tả mới sau khi chủ sửa");
            entityManager.flush(); // UPDATE của entity không được ghi đè checkin_count về 0
        });

        assertThat(checkinCount(placeId)).isEqualTo(1);
    }

    @Test
    void historyIsOwnAndPaged() throws Exception {
        Account member = account(true);
        Account other = account(true);
        List<Long> places = List.of(place("APPROVED", null), place("APPROVED", null), place("APPROVED", null));
        for (long placeId : places) {
            checkIn(member, placeId, north(10), 10).andExpect(status().isCreated());
        }
        checkIn(other, places.getFirst(), north(10), 10).andExpect(status().isCreated());

        MvcResult first = perform(member, get("/api/v1/me/check-ins?limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].placeId").value(places.get(2))) // mới nhất trước
                .andExpect(jsonPath("$.items[0].place.name").isNotEmpty())
                .andExpect(jsonPath("$.items[0].lat").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn();
        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.nextCursor");
        perform(member, get("/api/v1/me/check-ins?limit=2&cursor=" + cursor))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].placeId").value(places.getFirst()))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        mvc.perform(get("/api/v1/me/check-ins")).andExpect(status().isUnauthorized());
    }

    @Test
    void reviewLabelMeansCheckedInOnOrBeforeTheDayOfWriting() throws Exception {
        long placeId = place("APPROVED", null);
        Account before = account(true);
        Account sameDay = account(true);
        Account after = account(true);
        Account never = account(true);
        long reviewBefore = review(placeId, before, 0);
        long reviewSameDay = review(placeId, sameDay, 0);
        long reviewAfter = review(placeId, after, 3); // viết 3 ngày trước, check-in hôm nay
        long reviewNever = review(placeId, never, 0);
        jdbc.update("""
                INSERT INTO check_ins (user_id, place_id, location, accuracy_m, distance_m, checkin_date, created_at)
                VALUES (?, ?, ST_PointFromText('POINT(21.03 105.85)', 4326), 10, 0,
                        DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+07:00')) - INTERVAL 5 DAY,
                        UTC_TIMESTAMP(6) - INTERVAL 5 DAY)
                """, before.id(), placeId);
        // Viết review trước rồi mới bấm check-in trong cùng ngày — vẫn được nhãn
        checkIn(sameDay, placeId, north(10), 10).andExpect(status().isCreated());
        checkIn(after, placeId, north(10), 10).andExpect(status().isCreated());
        // Check-in của người khác không gắn nhãn cho review không check-in
        checkIn(account(true), placeId, north(10), 10).andExpect(status().isCreated());

        MvcResult page = mvc.perform(get("/api/v1/places/" + placeId + "/reviews?limit=10"))
                .andExpect(status().isOk())
                .andReturn();
        String json = page.getResponse().getContentAsString();

        assertThat(labelOf(json, reviewBefore)).isTrue();
        assertThat(labelOf(json, reviewSameDay)).isTrue();
        assertThat(labelOf(json, reviewAfter)).isFalse();
        assertThat(labelOf(json, reviewNever)).isFalse();
    }

    @Test
    void revisingTheReviewAfterARevisitEarnsTheLabel() throws Exception {
        Account member = account(true); // trước place(): người đề xuất là user đầu tiên trong CSDL
        long placeId = place("APPROVED", null);
        long reviewId = review(placeId, member, 30); // viết 1 tháng trước, chưa từng check-in
        checkIn(member, placeId, north(10), 10).andExpect(status().isCreated()); // quay lại hôm nay

        perform(member, get("/api/v1/reviews/" + reviewId))
                .andExpect(jsonPath("$.authorCheckedIn").value(false))
                .andExpect(jsonPath("$.revisedAt").isEmpty());

        // Gửi lại y nguyên → không tính là sửa, chưa có nhãn
        String current = perform(member, get("/api/v1/reviews/" + reviewId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        int rating = JsonPath.read(current, "$.rating");
        String content = JsonPath.read(current, "$.content");
        perform(
                        member,
                        patch("/api/v1/reviews/" + reviewId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonBody(rating, content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisedAt").isEmpty())
                .andExpect(jsonPath("$.authorCheckedIn").value(false));

        // Cập nhật theo lần ghé mới → mốc là hôm nay, check-in hôm nay → có nhãn
        perform(
                        member,
                        patch("/api/v1/reviews/" + reviewId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonBody(2, "Lần này quay lại thấy phục vụ chậm hơn hẳn, món nguội.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisedAt").isNotEmpty())
                .andExpect(jsonPath("$.authorCheckedIn").value(true));
        assertThat(jdbc.queryForObject(
                        "SELECT revised_at IS NOT NULL FROM reviews WHERE id = ?", Boolean.class, reviewId))
                .isTrue();
    }

    private static String jsonBody(int rating, String content) {
        return "{\"rating\": %d, \"content\": \"%s\"}".formatted(rating, content);
    }

    // ─── Hỗ trợ ─────────────────────────────────────────────────────────────

    private record Account(long id, String accessToken) {}

    private record Point(double lat, double lng) {}

    private static Point north(double meters) {
        return new Point(LAT + meters / METERS_PER_DEGREE_LAT, LNG);
    }

    private Account account(boolean verified) throws Exception {
        String email = "checkin-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử check-in"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        if (verified) {
            jdbc.update("UPDATE users SET email_verified_at = UTC_TIMESTAMP(6) WHERE id = ?", id);
        }
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new Account(id, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
    }

    private long place(String status, Long ownerId) {
        Long proposer = jdbc.queryForObject("SELECT MIN(id) FROM users", Long.class);
        Long category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'pho-bun'", Long.class);
        String slug = "checkin-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, owner_id, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES (?, ?, ?, 'Quán thử check-in', ?, '1 Phố Thử', 'Hà Nội',
                        ST_PointFromText('POINT(21.03 105.85)', 4326), ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, category, proposer, ownerId, slug, status);
        Long id = jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    /** Review chèn thẳng, đã đăng, viết {@code daysAgo} ngày trước. */
    private long review(long placeId, Account author, int daysAgo) {
        jdbc.update("""
                INSERT INTO reviews (place_id, user_id, rating, content, visited_at, status, ip_address, ip_prefix,
                                     ip_flagged, version, created_at, updated_at)
                VALUES (?, ?, 4, 'Nội dung đánh giá đủ dài để hợp lệ.', CURRENT_DATE - INTERVAL ? DAY, 'PUBLISHED',
                        '203.0.113.1', '203.0.113.0/24', FALSE, 0,
                        UTC_TIMESTAMP(6) - INTERVAL ? DAY, UTC_TIMESTAMP(6) - INTERVAL ? DAY)
                """, placeId, author.id(), daysAgo, daysAgo, daysAgo);
        Long id = jdbc.queryForObject(
                "SELECT id FROM reviews WHERE place_id = ? AND user_id = ?", Long.class, placeId, author.id());
        assertThat(id).isNotNull();
        return id;
    }

    private ResultActions checkIn(Account member, long placeId, Point at, double accuracyM) throws Exception {
        return perform(
                member,
                post("/api/v1/places/" + placeId + "/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(at.lat(), at.lng(), accuracyM)));
    }

    private static String body(double lat, double lng, double accuracyM) {
        return "{\"lat\":%s,\"lng\":%s,\"accuracyM\":%s}".formatted(lat, lng, accuracyM);
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    private int checkinCount(long placeId) {
        Integer count = jdbc.queryForObject("SELECT checkin_count FROM places WHERE id = ?", Integer.class, placeId);
        assertThat(count).isNotNull();
        return count;
    }

    private int rows(Account member) {
        Integer count =
                jdbc.queryForObject("SELECT COUNT(*) FROM check_ins WHERE user_id = ?", Integer.class, member.id());
        assertThat(count).isNotNull();
        return count;
    }

    private static boolean labelOf(String json, long reviewId) {
        List<Boolean> values = JsonPath.read(json, "$.items[?(@.id == " + reviewId + ")].authorCheckedIn");
        assertThat(values).hasSize(1);
        return values.getFirst();
    }
}
