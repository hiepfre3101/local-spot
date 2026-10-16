package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.localspot.service.TestImages;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Thông báo (UC19, FR-27) qua HTTP trên MySQL thật: mỗi sự kiện trong UC tạo đúng thông báo cho đúng người với ảnh chụp
 * dữ liệu hiển thị; danh sách mới nhất trước, lọc chưa đọc, đếm, đánh dấu đã đọc (của người khác → 404), đọc tất cả.
 * Đẩy realtime kiểm ở {@code NotificationWebSocketTests}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String NOTIFICATIONS = "/api/v1/me/notifications";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // ─── Sự kiện ────────────────────────────────────────────────────────────

    @Test
    void placeModerationNotifiesProposer() throws Exception {
        Account moderator = account("MODERATOR");
        Account proposer = account();
        long approved = place(proposer, "PENDING", null);
        long rejected = place(proposer, "PENDING", null);

        perform(moderator, decision("/api/v1/moderation/places/" + approved, "APPROVE", null))
                .andExpect(status().isNoContent());
        perform(moderator, decision("/api/v1/moderation/places/" + rejected, "REJECT", "Trùng địa điểm đã có"))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> items = notificationsOf(proposer);
        assertThat(items).extracting(n -> n.get("type")).containsExactly("PLACE_REJECTED", "PLACE_APPROVED");
        assertThat(data(items.get(0)))
                .containsEntry("placeId", (int) rejected)
                .containsEntry("placeName", "Quán thử thông báo")
                .containsEntry("reason", "Trùng địa điểm đã có");
        assertThat(data(items.get(1))).containsEntry("placeId", (int) approved).containsKey("placeSlug");
        assertThat(notificationsOf(moderator)).isEmpty(); // không tự gửi cho người thao tác
    }

    @Test
    void publishedReviewNotifiesOwnerAndModerationNotifiesAuthor() throws Exception {
        Account moderator = account("MODERATOR");
        Account owner = account("OWNER");
        long placeId = place(account(), "APPROVED", owner);

        // Viết thẳng (trust ≥ 30) → chủ nhận ngay; tác giả không cần thông báo (đã thấy kết quả trong response)
        Account trusted = account();
        jdbc.update("UPDATE users SET helpful_votes_count = 10 WHERE id = ?", trusted.id());
        perform(trusted, writeReview(placeId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        assertThat(notificationsOf(trusted)).isEmpty();

        // Hàng chờ: duyệt → tác giả REVIEW_PUBLISHED + chủ NEW_REVIEW_ON_MY_PLACE; từ chối → tác giả kèm lý do
        Account held = account();
        long heldReview = review(placeId, held, "PENDING");
        perform(moderator, decision("/api/v1/moderation/reviews/" + heldReview, "APPROVE", null))
                .andExpect(status().isNoContent());
        Account rejectedAuthor = account();
        long rejectedReview = review(placeId, rejectedAuthor, "PENDING");
        perform(moderator, decision("/api/v1/moderation/reviews/" + rejectedReview, "REJECT", "Quảng cáo"))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> ownerItems = notificationsOf(owner);
        assertThat(ownerItems)
                .extracting(n -> n.get("type"))
                .containsOnly("NEW_REVIEW_ON_MY_PLACE")
                .hasSize(2);
        assertThat(data(ownerItems.get(0)))
                .containsEntry("reviewId", (int) heldReview)
                .containsEntry("rating", 4)
                .containsEntry("authorName", "Người thử thông báo");
        assertThat(notificationsOf(held)).extracting(n -> n.get("type")).containsExactly("REVIEW_PUBLISHED");
        List<Map<String, Object>> rejected = notificationsOf(rejectedAuthor);
        assertThat(rejected).extracting(n -> n.get("type")).containsExactly("REVIEW_REJECTED");
        assertThat(data(rejected.getFirst())).containsEntry("reason", "Quảng cáo");
    }

    @Test
    void firstOwnerReplyNotifiesAuthorOnce() throws Exception {
        Account owner = account("OWNER");
        Account author = account();
        long reviewId = review(place(account(), "APPROVED", owner), author, "PUBLISHED");

        for (String content : List.of("Cảm ơn bạn!", "Cảm ơn bạn nhiều!")) {
            perform(
                            owner,
                            put("/api/v1/reviews/" + reviewId + "/reply")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"content\": \"" + content + "\"}"))
                    .andExpect(status().isOk());
        }
        List<Map<String, Object>> items = notificationsOf(author);
        assertThat(items).extracting(n -> n.get("type")).containsExactly("OWNER_REPLIED");
        assertThat(data(items.getFirst())).containsEntry("reviewId", (int) reviewId);
    }

    @Test
    void hidingReportedContentNotifiesAffectedUsersAndReporters() throws Exception {
        Account moderator = account("MODERATOR");
        Account author = account();
        Account owner = account("OWNER");
        Account proposer = account();
        long placeId = place(proposer, "APPROVED", owner);
        long reviewId = review(placeId, author, "PUBLISHED");
        Account reporter1 = account();
        Account reporter2 = account();
        report(reporter1, "REVIEW", reviewId, "FAKE");
        report(reporter2, "REVIEW", reviewId, "FAKE");

        perform(moderator, reportDecision("REVIEW", reviewId, "HIDE", null)).andExpect(status().isNoContent());

        List<Map<String, Object>> authorItems = notificationsOf(author);
        assertThat(authorItems).extracting(n -> n.get("type")).containsExactly("REPORT_ACTION_TAKEN");
        assertThat(data(authorItems.getFirst()))
                .containsEntry("targetType", "REVIEW")
                .containsEntry("targetId", (int) reviewId)
                .containsEntry("violation", true);
        for (Account reporter : List.of(reporter1, reporter2)) {
            assertThat(notificationsOf(reporter)).extracting(n -> n.get("type")).containsExactly("REPORT_RESOLVED");
        }

        // Ẩn địa điểm: người đề xuất và chủ đều được báo (O9: WRONG_INFO không phạt ai)
        Account placeReporter = account();
        perform(
                        placeReporter,
                        post("/api/v1/reports")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"targetType": "PLACE", "targetId": %d, "reason": "WRONG_INFO",
                                         "detail": "Quán đã đóng cửa"}
                                        """.formatted(placeId)))
                .andExpect(status().isCreated());
        perform(moderator, reportDecision("PLACE", placeId, "HIDE", "WRONG_INFO"))
                .andExpect(status().isNoContent());
        for (Account affected : List.of(proposer, owner)) {
            List<Map<String, Object>> items = notificationsOf(affected);
            assertThat(items).extracting(n -> n.get("type")).contains("REPORT_ACTION_TAKEN");
            assertThat(data(items.getFirst()))
                    .containsEntry("targetType", "PLACE")
                    .containsEntry("violation", false);
        }
        assertThat(notificationsOf(placeReporter))
                .extracting(n -> n.get("type"))
                .containsExactly("REPORT_RESOLVED");
    }

    @Test
    void dismissingReportsSendsNothing() throws Exception {
        Account author = account();
        long reviewId = review(place(account(), "APPROVED", null), author, "PUBLISHED");
        Account reporter = account();
        report(reporter, "REVIEW", reviewId, "SPAM");

        perform(account("MODERATOR"), reportDecision("REVIEW", reviewId, "DISMISS", null))
                .andExpect(status().isNoContent());
        assertThat(notificationsOf(author)).isEmpty();
        assertThat(notificationsOf(reporter)).isEmpty();
    }

    @Test
    void claimsNotifyModeratorsThenEveryClaimant() throws Exception {
        Account moderator = account("MODERATOR");
        long placeId = place(account(), "APPROVED", null);
        Account claimant = account();
        Account rival = account();
        submitClaim(claimant, placeId);
        submitClaim(rival, placeId);

        List<Map<String, Object>> modItems = notificationsOf(moderator);
        assertThat(modItems.stream().filter(n -> placeIdOf(n) == placeId))
                .extracting(n -> n.get("type"))
                .containsExactly("CLAIM_SUBMITTED", "CLAIM_SUBMITTED");

        long claimId = jdbc.queryForObject(
                "SELECT id FROM place_claims WHERE user_id = ? AND place_id = ?", Long.class, claimant.id(), placeId);
        perform(account("MODERATOR"), decision("/api/v1/moderation/claims/" + claimId, "APPROVE", null))
                .andExpect(status().isNoContent());

        assertThat(notificationsOf(claimant).stream().filter(n -> placeIdOf(n) == placeId))
                .extracting(n -> n.get("type"))
                .containsExactly("CLAIM_APPROVED");
        List<Map<String, Object>> rivalItems = notificationsOf(rival);
        assertThat(rivalItems).extracting(n -> n.get("type")).containsExactly("CLAIM_REJECTED");
        assertThat(data(rivalItems.getFirst())).containsEntry("reason", "Địa điểm đã được xác nhận cho một chủ khác.");
    }

    @Test
    void moderatorClaimingAPlaceIsNotNotifiedOfOwnClaim() throws Exception {
        Account moderatorClaimant = account("MODERATOR");
        long placeId = place(account(), "APPROVED", null);
        submitClaim(moderatorClaimant, placeId);
        assertThat(notificationsOf(moderatorClaimant).stream().filter(n -> placeIdOf(n) == placeId))
                .isEmpty();
    }

    // ─── Đọc / đánh dấu (UC19) ──────────────────────────────────────────────

    @Test
    void listCountAndMarkRead() throws Exception {
        Account user = account();
        long first = notification(user, "PLACE_APPROVED");
        long second = notification(user, "OWNER_REPLIED");
        long third = notification(user, "REVIEW_PUBLISHED");

        perform(user, get(NOTIFICATIONS + "/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));

        // Mới nhất trước, đi hết bằng cursor
        String page1 = perform(user, get(NOTIFICATIONS).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(third))
                .andExpect(jsonPath("$.items[1].id").value(second))
                .andExpect(jsonPath("$.items[0].readAt").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cursor = JsonPath.read(page1, "$.nextCursor");
        perform(user, get(NOTIFICATIONS).param("limit", "2").param("cursor", cursor))
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        perform(user, post(NOTIFICATIONS + "/" + second + "/read")).andExpect(status().isNoContent());
        String readAt = jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", String.class, second);
        perform(user, post(NOTIFICATIONS + "/" + second + "/read")).andExpect(status().isNoContent()); // idempotent
        assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", String.class, second))
                .isEqualTo(readAt);
        perform(user, get(NOTIFICATIONS).param("unreadOnly", "true"))
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.contains((int) third, (int) first)));

        // Thông báo của người khác: như không tồn tại
        perform(account(), post(NOTIFICATIONS + "/" + first + "/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        perform(user, post(NOTIFICATIONS + "/read-all")).andExpect(status().isNoContent());
        perform(user, get(NOTIFICATIONS + "/unread-count"))
                .andExpect(jsonPath("$.count").value(0));
        assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", String.class, second))
                .isEqualTo(readAt); // đọc tất cả không ghi đè thời điểm đã đọc
        mvc.perform(get(NOTIFICATIONS)).andExpect(status().isUnauthorized());
    }

    // ─── Dữ liệu thử ────────────────────────────────────────────────────────

    private record Account(long id, String accessToken) {}

    private Account account(String... roles) throws Exception {
        String email = "notify-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử thông báo"}
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

    private long place(Account proposer, String status, Account owner) {
        Long category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'pho-bun'", Long.class);
        String slug = "notify-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, owner_id, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES (?, ?, ?, 'Quán thử thông báo', ?, '1 Phố Thử', 'Hà Nội',
                        ST_PointFromText('POINT(21.03 105.85)', 4326), ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, category, proposer.id(), owner == null ? null : owner.id(), slug, status);
        Long id = jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    private long review(long placeId, Account author, String status) {
        jdbc.update("""
                INSERT INTO reviews (place_id, user_id, rating, content, visited_at, status, ip_address, ip_prefix,
                                     ip_flagged, version, created_at, updated_at)
                VALUES (?, ?, 4, 'Nội dung đánh giá đủ dài để hợp lệ.', CURRENT_DATE, ?, '203.0.113.1',
                        '203.0.113.0/24', FALSE, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, placeId, author.id(), status);
        Long id = jdbc.queryForObject(
                "SELECT id FROM reviews WHERE place_id = ? AND user_id = ?", Long.class, placeId, author.id());
        assertThat(id).isNotNull();
        return id;
    }

    private long notification(Account user, String type) {
        jdbc.update(
                "INSERT INTO notifications (user_id, type, data, created_at) VALUES (?, ?, '{}', UTC_TIMESTAMP(6))",
                user.id(),
                type);
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM notifications WHERE user_id = ?", Long.class, user.id());
        assertThat(id).isNotNull();
        return id;
    }

    private static AbstractMockHttpServletRequestBuilder<?> writeReview(long placeId) {
        String json = """
                {"rating": 5, "content": "Phở ngon, nước dùng trong và thơm.", "visitedAt": "%s"}
                """.formatted(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        return multipart("/api/v1/places/" + placeId + "/reviews")
                .file(new MockMultipartFile(
                        "review", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8)));
    }

    private void submitClaim(Account claimant, long placeId) throws Exception {
        perform(
                        claimant,
                        multipart("/api/v1/places/" + placeId + "/claims")
                                .file(new MockMultipartFile(
                                        "claim",
                                        "",
                                        MediaType.APPLICATION_JSON_VALUE,
                                        "{\"contactPhone\": \"0912345678\"}".getBytes(StandardCharsets.UTF_8)))
                                .file(new MockMultipartFile(
                                        "evidence", "0.jpg", "image/jpeg", TestImages.jpeg(48, 48))))
                .andExpect(status().isCreated());
    }

    private void report(Account reporter, String type, long targetId, String reason) throws Exception {
        perform(
                        reporter,
                        post("/api/v1/reports")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"targetType": "%s", "targetId": %d, "reason": "%s"}
                                        """.formatted(type, targetId, reason)))
                .andExpect(status().isCreated());
    }

    private static AbstractMockHttpServletRequestBuilder<?> decision(String base, String decision, String reason) {
        return post(base + "/decision").contentType(MediaType.APPLICATION_JSON).content("""
                        {"decision": "%s", "reason": %s}
                        """.formatted(
                        decision, reason == null ? "null" : "\"" + reason + "\""));
    }

    private static AbstractMockHttpServletRequestBuilder<?> reportDecision(
            String type, long targetId, String decision, String confirmedReason) {
        return post("/api/v1/moderation/reports/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetType": "%s", "targetId": %d, "decision": "%s", "confirmedReason": %s}
                        """.formatted(
                        type, targetId, decision, confirmedReason == null ? "null" : "\"" + confirmedReason + "\""));
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    /** Thông báo của người dùng, mới nhất trước (một trang 50 — đủ cho dữ liệu một test). */
    private List<Map<String, Object>> notificationsOf(Account user) throws Exception {
        String json = perform(user, get(NOTIFICATIONS).param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(json, "$.items");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> notification) {
        return (Map<String, Object>) notification.get("data");
    }

    private static long placeIdOf(Map<String, Object> notification) {
        Object id = data(notification).get("placeId");
        return id == null ? -1 : ((Number) id).longValue();
    }
}
