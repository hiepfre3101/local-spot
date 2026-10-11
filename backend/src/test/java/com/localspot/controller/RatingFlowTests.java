package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.localspot.entity.Place;
import com.localspot.event.PlaceIndexChangedEvent;
import com.localspot.service.RatingService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tính lại điểm Bayesian (FR-22) trên MySQL thật: sự kiện sau commit → listener {@code @Async} → cột điểm của địa điểm;
 * job đêm tính lại toàn bộ với C mới và đối soát. Điểm mong đợi tính độc lập trong test từ {@code COUNT} / {@code AVG}
 * bằng SQL (không gọi lại code của ứng dụng), C đọc tại thời điểm kiểm tra vì CSDL dùng chung với các lớp test khác.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class RatingFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String CONTENT = "Phở nước trong, thịt mềm, phục vụ nhanh.";
    private static final int M = 10;
    private static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RatingService ratingService;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void reviewLifecycleKeepsThePlaceScoreInSync() throws Exception {
        long placeId = approvedPlace();
        Account a = account(true);
        Account b = account(true);
        Account c = account(true);

        write(a, placeId, 5);
        write(b, placeId, 4);
        long cReview = write(c, placeId, 2);
        awaitRating(placeId, 3, "3.67");
        assertMatchesFormula(placeId);

        // Sửa sao của review PUBLISHED → R đổi
        perform(
                        c,
                        patch("/api/v1/reviews/" + cReview)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rating\": 5}"))
                .andExpect(status().isOk());
        awaitRating(placeId, 3, "4.67");
        assertMatchesFormula(placeId);

        // Review chờ duyệt (trust thấp) không tính; xóa review PUBLISHED → v giảm
        create(account(false), placeId, 1).andExpect(jsonPath("$.status").value("PENDING"));
        perform(c, delete("/api/v1/reviews/" + cReview)).andExpect(status().isNoContent());
        awaitRating(placeId, 2, "4.50");
        assertMatchesFormula(placeId);
    }

    @Test
    void placeWithoutPublishedReviewsScoresZero() throws Exception {
        long placeId = approvedPlace();
        Account a = account(true);
        long reviewId = write(a, placeId, 5);
        awaitRating(placeId, 1, "5.00");
        assertThat(rating(placeId).get("bayesian_score")).isNotEqualTo(new BigDecimal("0.000"));

        perform(a, delete("/api/v1/reviews/" + reviewId)).andExpect(status().isNoContent());
        awaitRating(placeId, 0, "0.00");
        assertThat(rating(placeId).get("bayesian_score"))
                .isEqualTo(new BigDecimal("0.000")); // chốt: v = 0 → 0, không phải C
    }

    /** Nhiều lần tính lại chen nhau trên cùng địa điểm: kết quả cuối phải phản ánh mọi review (khóa trước khi đếm). */
    @Test
    void concurrentReviewsOnOnePlaceEndConsistent() throws Exception {
        long placeId = approvedPlace();
        List<Account> authors = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            authors.add(account(true));
        }
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < authors.size(); i++) {
            Account author = authors.get(i);
            int stars = i % 5 + 1; // 1,2,3,4,5,1 → R = 16 / 6
            calls.add(() ->
                    create(author, placeId, stars).andReturn().getResponse().getStatus());
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(calls.size())) {
            for (Future<Integer> f : pool.invokeAll(calls)) {
                assertThat(f.get()).isEqualTo(201);
            }
        }
        awaitRating(placeId, 6, "2.67");
        Thread.sleep(500); // lần tính lại đến muộn không được ghi đè bằng dữ liệu cũ
        assertThat(rating(placeId))
                .containsEntry("review_count", 6L)
                .containsEntry("avg_rating", new BigDecimal("2.67"));
        assertMatchesFormula(placeId);
    }

    /** D4: job đêm tính lại mọi địa điểm — cũng sửa điểm lệch do sự kiện bị mất (ở đây: review chèn thẳng, không sự kiện). */
    @Test
    void nightlyRecalculationReconcilesEveryPlace() throws Exception {
        long missedEvents = approvedPlace();
        insertPublishedReview(missedEvents, account(true), 4);
        insertPublishedReview(missedEvents, account(true), 3);
        long drifted = approvedPlace();
        jdbc.update(
                "UPDATE places SET review_count = 99, avg_rating = 4.90, bayesian_score = 4.900 WHERE id = ?", drifted);

        ratingService.recalculateAll();

        assertThat(rating(missedEvents))
                .containsEntry("review_count", 2L)
                .containsEntry("avg_rating", new BigDecimal("3.50"));
        assertMatchesFormula(missedEvents);
        assertThat(rating(drifted))
                .containsEntry("review_count", 0L)
                .containsEntry("avg_rating", new BigDecimal("0.00"))
                .containsEntry("bayesian_score", new BigDecimal("0.000"));
        // Điểm đổi → index tìm kiếm được báo đồng bộ (điểm / số review nằm trong index)
        assertThat(indexedPlaceIds()).contains(missedEvents, drifted);

        // Chạy lại ngay: không gì đổi với hai địa điểm này → không báo đồng bộ lại
        events.clear();
        ratingService.recalculateAll();
        assertThat(indexedPlaceIds()).doesNotContain(missedEvents, drifted);
    }

    /** C chỉ gồm review trên địa điểm APPROVED: review 1 sao của địa điểm bị ẩn không được kéo điểm nơi khác xuống. */
    @Test
    void systemAverageIgnoresPlacesThatAreNotApproved() throws Exception {
        long hidden = approvedPlace();
        for (int i = 0; i < 12; i++) {
            insertPublishedReview(hidden, account(false), 1);
        }
        jdbc.update("UPDATE places SET status = 'HIDDEN' WHERE id = ?", hidden);
        long placeId = approvedPlace();
        insertPublishedReview(placeId, account(false), 5);

        ratingService.recalculateAll();

        assertMatchesFormula(placeId); // công thức trong test tính C chỉ trên địa điểm APPROVED
        assertThat(rating(hidden)).containsEntry("review_count", 12L); // địa điểm bị ẩn vẫn có điểm của mình
    }

    /** Hồi quy mất cập nhật: sửa địa điểm qua entity (chủ sửa, duyệt) không được ghi đè điểm vừa tính lại cùng lúc. */
    @Test
    void editingThePlaceThroughJpaKeepsTheScore() throws Exception {
        long placeId = approvedPlace();
        tx.executeWithoutResult(status -> {
            Place place = entityManager.find(Place.class, placeId);
            jdbc.update(
                    "UPDATE places SET review_count = 3, avg_rating = 4.33, bayesian_score = 4.123 WHERE id = ?",
                    placeId);
            place.setDescription("Chủ quán cập nhật mô tả trong lúc điểm vừa được tính lại.");
        });
        assertThat(rating(placeId))
                .containsEntry("review_count", 3L)
                .containsEntry("avg_rating", new BigDecimal("4.33"))
                .containsEntry("bayesian_score", new BigDecimal("4.123"));
    }

    // ─── Hỗ trợ ─────────────────────────────────────────────────────────────

    private record Account(long id, String accessToken) {}

    /** {@code trusted}: 10 + 18 + 2 = 30 → review đăng ngay; ngược lại chỉ xác thực email (trust 10) → chờ duyệt. */
    private Account account(boolean trusted) throws Exception {
        String email = "rating-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử điểm"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        jdbc.update("UPDATE users SET email_verified_at = UTC_TIMESTAMP(6) WHERE id = ?", id);
        if (trusted) {
            jdbc.update(
                    "UPDATE users SET created_at = UTC_TIMESTAMP(6) - INTERVAL 200 DAY, helpful_votes_count = 1"
                            + " WHERE id = ?",
                    id);
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

    /** Người đề xuất riêng (không phụ thuộc dữ liệu của lớp test khác, không trùng tác giả review). */
    private long approvedPlace() throws Exception {
        long proposer = account(false).id();
        Long category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'pho-bun'", Long.class);
        String slug = "rating-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES (?, ?, 'Quán thử điểm', ?, '1 Phố Thử', 'Hà Nội',
                        ST_PointFromText('POINT(21.03 105.85)', 4326), 'APPROVED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, category, proposer, slug);
        Long id = jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    /** Mỗi review một dải IP riêng — không vướng luật cảnh báo IP (U3). */
    private ResultActions create(Account author, long placeId, int rating) throws Exception {
        String json = """
                {"rating": %d, "content": "%s", "visitedAt": "%s"}
                """.formatted(rating, CONTENT, LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        String ip = "10." + IP.getAndIncrement() % 250 + "." + (IP.get() / 250) % 250 + ".7";
        return perform(
                author,
                multipart("/api/v1/places/" + placeId + "/reviews")
                        .file(new MockMultipartFile(
                                "review", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8)))
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        }));
    }

    private long write(Account author, long placeId, int rating) throws Exception {
        MvcResult result = create(author, placeId, rating)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void insertPublishedReview(long placeId, Account author, int rating) {
        jdbc.update("""
                INSERT INTO reviews (place_id, user_id, rating, content, visited_at, status, ip_address, ip_prefix,
                                     ip_flagged, version, created_at, updated_at)
                VALUES (?, ?, ?, 'Nội dung đánh giá đủ dài để hợp lệ.', CURRENT_DATE, 'PUBLISHED', '203.0.113.1',
                        '203.0.113.0/24', FALSE, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, placeId, author.id(), rating);
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    private Map<String, Object> rating(long placeId) {
        Map<String, Object> row =
                jdbc.queryForMap("SELECT review_count, avg_rating, bayesian_score FROM places WHERE id = ?", placeId);
        row.put("review_count", ((Number) row.get("review_count")).longValue());
        return row;
    }

    /** Chờ listener {@code @Async} tính xong — điểm đạt giá trị mong đợi trong 10 giây. */
    private void awaitRating(long placeId, long reviewCount, String avgRating) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        Map<String, Object> current = rating(placeId);
        while (System.currentTimeMillis() < deadline) {
            current = rating(placeId);
            if (current.get("review_count").equals(reviewCount)
                    && ((BigDecimal) current.get("avg_rating")).compareTo(new BigDecimal(avgRating)) == 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Điểm chưa được tính lại: " + current);
    }

    /** Điểm lưu khớp công thức tính độc lập từ SQL: (v/(v+m))·R + (m/(v+m))·C, C = AVG review PUBLISHED / địa điểm APPROVED. */
    private void assertMatchesFormula(long placeId) {
        Map<String, Object> stats = jdbc.queryForMap(
                "SELECT COUNT(*) AS v, AVG(rating) AS r FROM reviews"
                        + " WHERE place_id = ? AND status = 'PUBLISHED' AND deleted_at IS NULL",
                placeId);
        Double c = jdbc.queryForObject("""
                SELECT AVG(r.rating) FROM reviews r JOIN places p ON p.id = r.place_id
                WHERE r.status = 'PUBLISHED' AND r.deleted_at IS NULL AND p.status = 'APPROVED'
                """, Double.class);
        double v = ((Number) stats.get("v")).doubleValue();
        double r = ((Number) stats.get("r")).doubleValue();
        double expected = v / (v + M) * r + (double) M / (v + M) * c;
        assertThat(((BigDecimal) rating(placeId).get("bayesian_score")).doubleValue())
                .isCloseTo(expected, within(0.0005));
    }

    private List<Long> indexedPlaceIds() {
        return events.stream(PlaceIndexChangedEvent.class)
                .flatMap(e -> e.placeIds().stream())
                .toList();
    }
}
