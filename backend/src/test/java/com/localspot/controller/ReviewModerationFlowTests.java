package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/**
 * Duyệt review trong hàng chờ (UC28) qua HTTP trên MySQL thật: hàng chờ FIFO kèm tín hiệu chống review ảo (trust, vi
 * phạm, cờ IP, review cùng dải), màn duyệt một review, duyệt (tính lại điểm), từ chối (lý do + một vi phạm), chỉ review
 * PENDING, version lệch, hai kiểm duyệt viên cùng lúc (3b), không tự duyệt, phân quyền, activity_log. Như các flow test
 * khác: không @Transactional, mỗi test tạo dữ liệu riêng; hàng chờ dùng chung CSDL nên chỉ khẳng định về review do test
 * tạo ra.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewModerationFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String QUEUE = "/api/v1/moderation/reviews";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // ─── Hàng chờ (UC28 bước 1) ─────────────────────────────────────────────

    @Test
    void queueListsPendingReviewsOldestFirstWithAntiFraudSignals() throws Exception {
        Account moderator = account("MODERATOR");
        Account flaggedAuthor = account();
        Account lowTrustAuthor = account();
        long placeId = place("APPROVED");
        String subnet = uniqueSubnet();
        long companion = review(
                placeId, account(), "PUBLISHED", subnet, false, Instant.now().minusSeconds(3600));
        long flagged = review(placeId, flaggedAuthor, "PENDING", subnet, true, Instant.now());
        long lowTrust = review(placeId, lowTrustAuthor, "PENDING", uniqueSubnet(), false, Instant.now());
        violation(lowTrustAuthor, Instant.now().plus(Duration.ofDays(10)));
        violation(lowTrustAuthor, Instant.now().minus(Duration.ofDays(1))); // đã hết hạn — không tính
        // Không vào hàng chờ: review đã đăng, review chờ trên địa điểm đã bị ẩn
        long onHiddenPlace = review(place("HIDDEN"), account(), "PENDING", uniqueSubnet(), false, Instant.now());

        List<Map<String, Object>> items = allQueueItems(moderator, null);
        List<Long> ids =
                items.stream().map(ReviewModerationFlowTests::reviewIdOf).toList();
        assertThat(ids).doesNotHaveDuplicates().doesNotContain(companion, onHiddenPlace);
        assertThat(ids.stream().filter(id -> id == flagged || id == lowTrust)).containsExactly(flagged, lowTrust);

        Map<String, Object> flaggedItem = itemOf(items, flagged);
        assertThat(flaggedItem)
                .containsEntry("ipFlagged", true)
                .containsEntry("sameSubnetReviewCount24h", 1)
                .containsEntry("authorTrustScore", 10)
                .containsEntry("authorActiveViolations", 0);
        assertThat(JsonPath.<Integer>read(flaggedItem, "$.place.id").longValue())
                .isEqualTo(placeId);
        assertThat(JsonPath.<String>read(flaggedItem, "$.review.status")).isEqualTo("PENDING");
        assertThat(itemOf(items, lowTrust))
                .containsEntry("ipFlagged", false)
                .containsEntry("sameSubnetReviewCount24h", 0)
                .containsEntry("authorTrustScore", 0)
                .containsEntry("authorActiveViolations", 1);

        // Lọc theo cờ IP
        List<Long> flaggedOnly = allQueueItems(moderator, true).stream()
                .map(ReviewModerationFlowTests::reviewIdOf)
                .toList();
        assertThat(flaggedOnly).contains(flagged).doesNotContain(lowTrust);
    }

    @Test
    void detailShowsAuthorHistoryAndSameSubnetReviews() throws Exception {
        Account moderator = account("MODERATOR");
        Account author = account();
        long placeId = place("APPROVED");
        String subnet = uniqueSubnet();
        Instant now = Instant.now();
        long reviewId = review(placeId, author, "PENDING", subnet, true, now);
        long near = review(placeId, account(), "PUBLISHED", subnet, false, now.minus(Duration.ofHours(2)));
        long far = review(placeId, account(), "PUBLISHED", subnet, false, now.minus(Duration.ofHours(30)));
        review(place("APPROVED"), author, "REJECTED", uniqueSubnet(), false, now.minus(Duration.ofDays(3)));
        review(place("APPROVED"), author, "PUBLISHED", uniqueSubnet(), false, now.minus(Duration.ofDays(5)));

        String json = perform(moderator, get(QUEUE + "/" + reviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.review.id").value(reviewId))
                .andExpect(jsonPath("$.item.sameSubnetReviewCount24h").value(1))
                .andExpect(jsonPath("$.authorJoinedAt").isString())
                .andExpect(jsonPath("$.authorReviewCounts.PENDING").value(1))
                .andExpect(jsonPath("$.authorReviewCounts.PUBLISHED").value(1))
                .andExpect(jsonPath("$.authorReviewCounts.REJECTED").value(1))
                .andExpect(jsonPath("$.authorReviewCounts.HIDDEN").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Integer> sameSubnet = JsonPath.read(json, "$.sameSubnetReviews[*].id");
        assertThat(sameSubnet.stream().map(Integer::longValue).toList())
                .containsExactly(near)
                .doesNotContain(far);

        perform(moderator, get(QUEUE + "/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    }

    // ─── Quyết định (UC28 bước 3–5, 3a) ─────────────────────────────────────

    @Test
    void approvePublishesAndRecalculatesRating() throws Exception {
        Account moderator = account("MODERATOR");
        Account author = account();
        long placeId = place("APPROVED");
        long reviewId = review(placeId, author, "PENDING", uniqueSubnet(), false, Instant.now());

        perform(moderator, decision(reviewId, "APPROVE", null, null)).andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, moderated_by, moderated_at, reject_reason FROM reviews WHERE id = ?", reviewId);
        assertThat(row).containsEntry("status", "PUBLISHED").containsEntry("reject_reason", null);
        assertThat(((Number) row.get("moderated_by")).longValue()).isEqualTo(moderator.id());
        assertThat(row.get("moderated_at")).isNotNull();
        assertThat(violationsOf(author)).isZero();
        awaitReviewCount(placeId, 1);
        assertThat(((Number) jdbc.queryForObject(
                                "SELECT actor_id FROM activity_log WHERE action = 'REVIEW_APPROVE' AND target_id = ?",
                                Long.class,
                                reviewId))
                        .longValue())
                .isEqualTo(moderator.id());

        // Đã xử lý thì không quyết định lại (không đảo quyết định)
        perform(account("MODERATOR"), decision(reviewId, "REJECT", "Spam", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_MODERATED"));
        perform(moderator, get("/api/v1/places/" + placeId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(reviewId));
    }

    @Test
    void rejectNeedsReasonAndRecordsOneViolation() throws Exception {
        Account moderator = account("MODERATOR");
        Account author = account();
        long reviewId = review(place("APPROVED"), author, "PENDING", uniqueSubnet(), false, Instant.now());

        for (String reason : new String[] {null, "   "}) {
            perform(moderator, decision(reviewId, "REJECT", reason, null))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("reason"));
        }
        perform(moderator, decision(reviewId, "REJECT", "a".repeat(501), null))
                .andExpect(status().isUnprocessableContent());
        assertThat(violationsOf(author)).isZero();

        perform(moderator, decision(reviewId, "REJECT", "  Nội dung quảng cáo  ", null))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForMap("SELECT status, reject_reason FROM reviews WHERE id = ?", reviewId))
                .containsEntry("status", "REJECTED")
                .containsEntry("reject_reason", "Nội dung quảng cáo");
        Map<String, Object> violation = jdbc.queryForMap(
                "SELECT source_type, source_id, points, created_by,"
                        + " TIMESTAMPDIFF(HOUR, UTC_TIMESTAMP(6), expires_at) AS ttl_hours"
                        + " FROM user_violations WHERE user_id = ?",
                author.id());
        assertThat(violation).containsEntry("source_type", "REVIEW_REJECTED");
        assertThat(((Number) violation.get("source_id")).longValue()).isEqualTo(reviewId);
        assertThat(((Number) violation.get("points")).intValue()).isEqualTo(25);
        assertThat(((Number) violation.get("created_by")).longValue()).isEqualTo(moderator.id());
        assertThat(((Number) violation.get("ttl_hours")).longValue())
                .isBetween(29L * 24, 30L * 24); // hết hạn sau 30 ngày
        String metadata = jdbc.queryForObject(
                "SELECT metadata FROM activity_log WHERE action = 'REVIEW_REJECT' AND target_id = ?",
                String.class,
                reviewId);
        assertThat(JsonPath.<String>read(metadata, "$.reason")).isEqualTo("Nội dung quảng cáo");

        // Tác giả thấy lý do; trust bị trừ (10 − 75 → 0)
        perform(author, get("/api/v1/me/reviews"))
                .andExpect(jsonPath("$.items[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].rejectReason").value("Nội dung quảng cáo"));
        perform(author, get("/api/v1/me")).andExpect(jsonPath("$.trustScore").value(0));
    }

    // ─── Ngoại lệ (UC28 3b) và luật kiểm duyệt ──────────────────────────────

    @Test
    void staleVersionIsRejected() throws Exception {
        Account moderator = account("MODERATOR");
        long reviewId = review(place("APPROVED"), account(), "PENDING", uniqueSubnet(), false, Instant.now());
        jdbc.update("UPDATE reviews SET version = version + 1 WHERE id = ?", reviewId); // tác giả vừa sửa

        perform(moderator, decision(reviewId, "APPROVE", null, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        perform(moderator, decision(reviewId, "APPROVE", null, 1)).andExpect(status().isNoContent());
    }

    @Test
    void concurrentRejectionsRecordOneViolation() throws Exception {
        Account author = account();
        long reviewId = review(place("APPROVED"), author, "PENDING", uniqueSubnet(), false, Instant.now());
        List<Account> moderators = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            moderators.add(account("MODERATOR"));
        }
        ExecutorService pool = Executors.newFixedThreadPool(moderators.size());
        try {
            List<Callable<MvcResult>> clicks = new ArrayList<>();
            for (Account moderator : moderators) {
                clicks.add(() -> perform(moderator, decision(reviewId, "REJECT", "Spam", null))
                        .andReturn());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<MvcResult> f : pool.invokeAll(clicks)) {
                statuses.add(f.get().getResponse().getStatus());
            }
            assertThat(statuses).containsOnlyOnce(204).containsOnly(204, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(violationsOf(author)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM activity_log WHERE action = 'REVIEW_REJECT' AND target_id = ?",
                        Integer.class,
                        reviewId))
                .isEqualTo(1);
    }

    @Test
    void moderatorCannotDecideOwnReviewOrReviewOnOwnPlace() throws Exception {
        Account moderator = account("MODERATOR");
        long own = review(place("APPROVED"), moderator, "PENDING", uniqueSubnet(), false, Instant.now());
        long ownedPlace = place("APPROVED");
        jdbc.update("UPDATE places SET owner_id = ? WHERE id = ?", moderator.id(), ownedPlace);
        long onOwnPlace = review(ownedPlace, account(), "PENDING", uniqueSubnet(), false, Instant.now());

        for (long reviewId : new long[] {own, onOwnPlace}) {
            perform(moderator, decision(reviewId, "REJECT", "Spam", null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));
        }
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM reviews WHERE id IN (?, ?) AND status = 'PENDING'",
                        Integer.class,
                        own,
                        onOwnPlace))
                .isEqualTo(2);
    }

    @Test
    void onlyReviewModeratorsMayUseTheQueue() throws Exception {
        long reviewId = review(place("APPROVED"), account(), "PENDING", uniqueSubnet(), false, Instant.now());
        for (Account actor : List.of(account(), account("OWNER"))) {
            perform(actor, get(QUEUE)).andExpect(status().isForbidden());
            perform(actor, get(QUEUE + "/" + reviewId)).andExpect(status().isForbidden());
            perform(actor, decision(reviewId, "APPROVE", null, null)).andExpect(status().isForbidden());
        }
        mvc.perform(get(QUEUE)).andExpect(status().isUnauthorized());
        perform(account("ADMIN"), get(QUEUE)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM reviews WHERE id = ?", String.class, reviewId))
                .isEqualTo("PENDING");
    }

    // ─── Dữ liệu thử ────────────────────────────────────────────────────────

    private record Account(long id, String accessToken) {}

    /** Tài khoản đã xác thực email, vừa tạo → trust 10. */
    private Account account(String... roles) throws Exception {
        String email = "review-mod-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử duyệt review"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        jdbc.update("UPDATE users SET email_verified_at = UTC_TIMESTAMP(6) WHERE id = ?", id);
        for (String role : roles) {
            jdbc.update(
                    "INSERT IGNORE INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = ?",
                    id,
                    role);
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

    private long place(String status) throws Exception {
        Account proposer = account();
        Long category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'pho-bun'", Long.class);
        String slug = "review-mod-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES (?, ?, 'Quán thử duyệt review', ?, '1 Phố Thử', 'Hà Nội',
                        ST_PointFromText('POINT(21.03 105.85)', 4326), ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, category, proposer.id(), slug, status);
        Long id = jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    /** Review chèn thẳng — cổng chống review ảo có test riêng (ReviewFlowTests); ở đây chỉ cần trạng thái / dải IP. */
    private long review(long placeId, Account author, String status, String ipPrefix, boolean ipFlagged, Instant at) {
        jdbc.update("""
                INSERT INTO reviews (place_id, user_id, rating, content, visited_at, status, ip_address, ip_prefix,
                                     ip_flagged, version, created_at, updated_at)
                VALUES (?, ?, 4, 'Nội dung đánh giá đủ dài để hợp lệ.', CURRENT_DATE, ?, '198.51.100.7', ?, ?, 0, ?, ?)
                """, placeId, author.id(), status, ipPrefix, ipFlagged, Timestamp.from(at), Timestamp.from(at));
        Long id = jdbc.queryForObject(
                "SELECT id FROM reviews WHERE place_id = ? AND user_id = ?", Long.class, placeId, author.id());
        assertThat(id).isNotNull();
        return id;
    }

    /** Dải IP riêng cho mỗi test — hàng chờ dùng chung CSDL, không để review của test khác lọt vào phép đếm. */
    private static String uniqueSubnet() {
        return "test-" + UUID.randomUUID().toString().substring(0, 18);
    }

    private void violation(Account user, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO user_violations (user_id, source_type, source_id, points, expires_at, created_at)
                VALUES (?, 'REPORT_CONFIRMED', 1, 25, ?, UTC_TIMESTAMP(6))
                """, user.id(), Timestamp.from(expiresAt));
    }

    private AbstractMockHttpServletRequestBuilder<?> decision(
            long reviewId, String decision, String reason, Integer version) {
        return post(QUEUE + "/" + reviewId + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decision": "%s", "reason": %s, "version": %s}
                        """.formatted(decision, reason == null ? "null" : "\"" + reason + "\"", version));
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    /** Đi hết hàng chờ bằng cursor (trang 50) — kiểm luôn phân trang không trùng / sót. */
    private List<Map<String, Object>> allQueueItems(Account moderator, Boolean ipFlagged) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        String cursor = null;
        do {
            AbstractMockHttpServletRequestBuilder<?> request = get(QUEUE).param("limit", "50");
            if (ipFlagged != null) {
                request = request.param("ipFlagged", ipFlagged.toString());
            }
            if (cursor != null) {
                request = request.param("cursor", cursor);
            }
            String json = perform(moderator, request)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            items.addAll(JsonPath.read(json, "$.items"));
            cursor = JsonPath.read(json, "$.nextCursor");
        } while (cursor != null);
        return items;
    }

    private static long reviewIdOf(Map<String, Object> item) {
        return JsonPath.<Integer>read(item, "$.review.id").longValue();
    }

    private static Map<String, Object> itemOf(List<Map<String, Object>> items, long reviewId) {
        return items.stream()
                .filter(item -> reviewIdOf(item) == reviewId)
                .findFirst()
                .orElseThrow();
    }

    private int violationsOf(Account user) {
        Integer count =
                jdbc.queryForObject("SELECT COUNT(*) FROM user_violations WHERE user_id = ?", Integer.class, user.id());
        return count == null ? 0 : count;
    }

    private void awaitReviewCount(long placeId, long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        Long current = null;
        while (System.currentTimeMillis() < deadline) {
            current = jdbc.queryForObject("SELECT review_count FROM places WHERE id = ?", Long.class, placeId);
            if (current != null && current == expected) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Điểm chưa được tính lại: review_count = " + current);
    }
}
