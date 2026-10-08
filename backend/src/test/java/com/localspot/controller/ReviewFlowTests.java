package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.localspot.entity.ReviewStatus;
import com.localspot.event.ReviewChangedEvent;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Checklist E6 qua HTTP trên MySQL thật: viết review với cổng chống review ảo (UC12, FR-17, FR-23, U3, U4, D2), sửa /
 * xóa của mình theo luật trạng thái (chốt 2026-10-08), danh sách (FR-14). Sự kiện tính lại rating được ghi lại bằng
 * {@link RecordApplicationEvents} (listener là mục sau).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class ReviewFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String CONTENT = "Phở nước trong, thịt mềm, phục vụ nhanh.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents events;

    // ─── Viết review (UC12) ──────────────────────────────────────────────────

    @Test
    void trustedMemberIsPublishedImmediatelyAndShownOnThePlace() throws Exception {
        Account author = account(Trust.TRUSTED);
        long placeId = approvedPlace();

        MvcResult created = create(author, placeId, 5, "   " + CONTENT + "   ", today(), "203.0.113.7")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.content").value(CONTENT)) // bỏ khoảng trắng hai đầu
                .andExpect(jsonPath("$.author.id").value(author.id()))
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.placeId").value(placeId))
                .andExpect(jsonPath("$.helpfulCount").value(0))
                .andExpect(jsonPath("$.ownerReply").isEmpty())
                .andReturn();
        long reviewId = idOf(created);

        assertThat(changes()).containsExactly(new ReviewChangedEvent(reviewId, placeId, null, ReviewStatus.PUBLISHED));
        assertThat(jdbc.queryForMap("SELECT ip_address, ip_prefix, ip_flagged FROM reviews WHERE id = ?", reviewId))
                .containsEntry("ip_address", "203.0.113.7")
                .containsEntry("ip_prefix", "203.0.113.0/24")
                .containsEntry("ip_flagged", false);
        assertThat(reviewIds(mvc.perform(get(listOf(placeId)))
                        .andExpect(status().isOk())
                        .andReturn()))
                .containsExactly(reviewId);
        // Trang chi tiết biết người xem đã có review (D2 — ẩn nút "Viết đánh giá")
        String slug = jdbc.queryForObject("SELECT slug FROM places WHERE id = ?", String.class, placeId);
        perform(author, get("/api/v1/places/" + slug))
                .andExpect(jsonPath("$.myReviewId").value(reviewId));
    }

    @Test
    void lowTrustMemberGoesToTheQueueAndOnlySeesItThemselves() throws Exception {
        Account author = account(Trust.NEW_VERIFIED); // trust = 10 < 30
        long placeId = approvedPlace();

        long reviewId = idOf(create(author, placeId, 2, CONTENT, today(), "198.51.100.1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn());

        assertThat(changes()).isEmpty(); // chưa công khai → chưa tính lại rating (UC12 7a)
        mvc.perform(get("/api/v1/places/" + placeId + "/reviews"))
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/users/" + author.id() + "/reviews"))
                .andExpect(jsonPath("$.items").isEmpty());
        perform(author, get("/api/v1/me/reviews"))
                .andExpect(jsonPath("$.items[0].id").value(reviewId))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    }

    @Test
    void unverifiedEmailIsRejected() throws Exception {
        Account author = account(Trust.UNVERIFIED);
        create(author, approvedPlace(), 4, CONTENT, today(), "198.51.100.2")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void onePerPlaceEvenAfterDeleting() throws Exception {
        Account author = account(Trust.TRUSTED);
        long placeId = approvedPlace();
        long reviewId = idOf(create(author, placeId, 4, CONTENT, today(), "198.51.100.3")
                .andExpect(status().isCreated())
                .andReturn());

        create(author, placeId, 5, CONTENT, today(), "198.51.100.3")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));

        perform(author, delete("/api/v1/reviews/" + reviewId)).andExpect(status().isNoContent());
        assertThat(changes()).last().isEqualTo(new ReviewChangedEvent(reviewId, placeId, ReviewStatus.PUBLISHED, null));
        mvc.perform(get("/api/v1/places/" + placeId + "/reviews"))
                .andExpect(jsonPath("$.items").isEmpty());
        // Review đã xóa vẫn chiếm chỗ UNIQUE(place_id, user_id) — D2
        create(author, placeId, 5, CONTENT, today(), "198.51.100.3")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));
        perform(author, delete("/api/v1/reviews/" + reviewId)).andExpect(status().isNotFound());
    }

    @Test
    void ownerCannotReviewTheirOwnPlaceAndOnlyApprovedPlacesCanBeReviewed() throws Exception {
        Account owner = account(Trust.TRUSTED);
        long owned = approvedPlace();
        jdbc.update("UPDATE places SET owner_id = ? WHERE id = ?", owner.id(), owned);
        create(owner, owned, 5, CONTENT, today(), "198.51.100.4")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));

        long pending = approvedPlace();
        jdbc.update("UPDATE places SET status = 'PENDING' WHERE id = ?", pending);
        create(owner, pending, 5, CONTENT, today(), "198.51.100.4")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
        mvc.perform(get("/api/v1/places/" + pending + "/reviews")).andExpect(status().isNotFound());
    }

    @Test
    void validatesRatingContentAndVisitDate() throws Exception {
        Account author = account(Trust.TRUSTED);
        long placeId = approvedPlace();

        create(author, placeId, 6, CONTENT, today(), "198.51.100.5")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("rating"));
        // 19 ký tự sau khi bỏ khoảng trắng hai đầu (U4)
        create(author, placeId, 4, "   " + "a".repeat(19) + "   ", today(), "198.51.100.5")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("content"));
        create(author, placeId, 4, CONTENT, today().plusDays(1), "198.51.100.5")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("visitedAt"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE user_id = ?", Long.class, author.id()))
                .isZero();
    }

    // ─── Chống review ảo (FR-23) ─────────────────────────────────────────────

    @Test
    void atMostFiveReviewsPerDayCountingDeletedOnes() throws Exception {
        Account author = account(Trust.TRUSTED);
        List<Long> reviewIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            reviewIds.add(idOf(create(author, approvedPlace(), 4, CONTENT, today(), "198.51.100.6")
                    .andExpect(status().isCreated())
                    .andReturn()));
        }
        perform(author, delete("/api/v1/reviews/" + reviewIds.getFirst())).andExpect(status().isNoContent());

        MvcResult limited = create(author, approvedPlace(), 4, CONTENT, today(), "198.51.100.6")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andReturn();
        long retryAfter = Long.parseLong(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfter).isCloseTo(24 * 3600, within(120L)); // tới lúc review cũ nhất rời cửa sổ

        // Review cũ nhất rời cửa sổ 24 giờ → có lượt mới
        jdbc.update(
                "UPDATE reviews SET created_at = UTC_TIMESTAMP(6) - INTERVAL 25 HOUR WHERE id = ?",
                reviewIds.getFirst());
        create(author, approvedPlace(), 4, CONTENT, today(), "198.51.100.6").andExpect(status().isCreated());
    }

    @Test
    void fourthReviewFromTheSameSubnetGoesToTheQueueFlagged() throws Exception {
        long placeId = approvedPlace();
        for (int host = 1; host <= 3; host++) {
            create(account(Trust.TRUSTED), placeId, 5, CONTENT, today(), "192.0.2." + host)
                    .andExpect(jsonPath("$.status").value("PUBLISHED"));
        }

        long flagged = idOf(create(account(Trust.TRUSTED), placeId, 5, CONTENT, today(), "192.0.2.200")
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn());
        assertThat(jdbc.queryForObject("SELECT ip_flagged FROM reviews WHERE id = ?", Boolean.class, flagged))
                .isTrue();
        // Dải khác, cùng địa điểm → bình thường; cùng dải, địa điểm khác → bình thường
        create(account(Trust.TRUSTED), placeId, 5, CONTENT, today(), "198.51.100.7")
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        create(account(Trust.TRUSTED), approvedPlace(), 5, CONTENT, today(), "192.0.2.201")
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    // ─── Sửa / xóa của mình (FR-18, chốt 2026-10-08) ─────────────────────────

    @Test
    void editingFollowsTheStatusRules() throws Exception {
        Account author = account(Trust.TRUSTED);
        long placeId = approvedPlace();
        long reviewId = idOf(create(author, placeId, 3, CONTENT, today(), "198.51.100.8")
                .andExpect(status().isCreated())
                .andReturn());

        // Tác giả tin cậy sửa sao → vẫn PUBLISHED, rating cần tính lại
        perform(author, patchJson(reviewId, "{\"rating\": 5, \"version\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(changes())
                .last()
                .isEqualTo(new ReviewChangedEvent(reviewId, placeId, ReviewStatus.PUBLISHED, ReviewStatus.PUBLISHED));
        // version cũ → 409
        perform(author, patchJson(reviewId, "{\"rating\": 4, \"version\": 0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        // REJECTED → gửi lại: về PENDING dù tác giả tin cậy, xóa lý do từ chối
        jdbc.update("UPDATE reviews SET status = 'REJECTED', reject_reason = 'Quảng cáo' WHERE id = ?", reviewId);
        perform(author, patchJson(reviewId, "{\"content\": \"" + CONTENT + " Đã sửa lại cho đúng.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.rejectReason").isEmpty());

        // HIDDEN (bị ẩn sau báo cáo) → khóa
        jdbc.update("UPDATE reviews SET status = 'HIDDEN' WHERE id = ?", reviewId);
        perform(author, patchJson(reviewId, "{\"rating\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_LOCKED"));

        // Không phải tác giả → 403; validate như khi tạo
        Account other = account(Trust.TRUSTED);
        perform(other, patchJson(reviewId, "{\"rating\": 1}")).andExpect(status().isForbidden());
        perform(other, delete("/api/v1/reviews/" + reviewId)).andExpect(status().isForbidden());
        jdbc.update("UPDATE reviews SET status = 'PUBLISHED' WHERE id = ?", reviewId);
        perform(author, patchJson(reviewId, "{\"content\": \"quá ngắn\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("content"));
    }

    @Test
    void lowTrustAuthorEditingAPublishedReviewSendsItBackToTheQueue() throws Exception {
        Account author = account(Trust.TRUSTED);
        long placeId = approvedPlace();
        long reviewId = idOf(create(author, placeId, 4, CONTENT, today(), "198.51.100.9")
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andReturn());
        // Trust tụt dưới ngưỡng (vd. vi phạm) — ở đây: mất điểm vote hữu ích
        jdbc.update("UPDATE users SET helpful_votes_count = 0 WHERE id = ?", author.id());

        perform(author, patchJson(reviewId, "{\"content\": \"" + CONTENT + " Thêm chi tiết.\"}"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(changes())
                .last()
                .isEqualTo(new ReviewChangedEvent(reviewId, placeId, ReviewStatus.PUBLISHED, ReviewStatus.PENDING));
        mvc.perform(get("/api/v1/places/" + placeId + "/reviews"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    // ─── Danh sách (FR-14) ──────────────────────────────────────────────────

    @Test
    void listsPublishedReviewsByNewestOrHelpfulnessWithPerViewerFields() throws Exception {
        long placeId = approvedPlace();
        Account a = account(Trust.TRUSTED);
        Account b = account(Trust.TRUSTED);
        Account c = account(Trust.TRUSTED);
        long ra = idOf(create(a, placeId, 5, CONTENT, today(), "10.0.1.1").andReturn());
        long rb = idOf(create(b, placeId, 3, CONTENT, today(), "10.0.2.1").andReturn());
        long rc = idOf(create(c, placeId, 5, CONTENT, today(), "10.0.3.1").andReturn());
        jdbc.update("UPDATE reviews SET helpful_count = 7 WHERE id = ?", rb);
        jdbc.update("UPDATE reviews SET helpful_count = 2 WHERE id IN (?, ?)", ra, rc);
        jdbc.update(
                "INSERT INTO review_votes (review_id, user_id, counted, created_at) VALUES (?, ?, TRUE, UTC_TIMESTAMP(6))",
                rb,
                a.id());
        jdbc.update("""
                INSERT INTO comments (review_id, user_id, content, status, created_at, updated_at)
                VALUES (?, ?, 'Đồng ý!', 'VISIBLE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                       (?, ?, 'Bị ẩn', 'HIDDEN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, rb, c.id(), rb, c.id());
        jdbc.update("""
                INSERT INTO owner_replies (review_id, user_id, content, created_at, updated_at)
                VALUES (?, ?, 'Cảm ơn bạn!', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, rb, c.id());

        assertThat(reviewIds(mvc.perform(get(listOf(placeId))).andReturn())).containsExactly(rc, rb, ra);
        assertThat(reviewIds(
                        mvc.perform(get(listOf(placeId)).param("rating", "5")).andReturn()))
                .containsExactly(rc, ra);

        // HELPFUL: keyset (helpful_count, id) qua từng trang 1 mục
        List<Long> paged = new ArrayList<>();
        String cursor = null;
        do {
            var request = get(listOf(placeId)).param("sort", "HELPFUL").param("limit", "1");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            MvcResult page = mvc.perform(request).andExpect(status().isOk()).andReturn();
            paged.addAll(reviewIds(page));
            cursor = JsonPath.read(page.getResponse().getContentAsString(), "$.nextCursor");
            if (paged.size() == 1) {
                mvc.perform(get(listOf(placeId)).param("sort", "NEWEST").param("cursor", cursor))
                        .andExpect(status().isUnprocessableContent())
                        .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
            }
        } while (cursor != null);
        assertThat(paged).containsExactly(rb, rc, ra);

        // Trường theo người xem / đếm theo lô
        String path = "$.items[?(@.id == " + rb + ")]";
        perform(a, get(listOf(placeId)))
                .andExpect(jsonPath(path + ".votedByMe").value(true))
                .andExpect(jsonPath(path + ".commentCount").value(1)) // bình luận bị ẩn không tính
                .andExpect(jsonPath(path + ".ownerReply.content").value("Cảm ơn bạn!"))
                .andExpect(jsonPath(path + ".ownerReply.owner.id").value((int) c.id()));
        mvc.perform(get(listOf(placeId)))
                .andExpect(jsonPath(path + ".votedByMe").value(false));

        // Lý do từ chối chỉ tác giả thấy
        jdbc.update("UPDATE reviews SET status = 'REJECTED', reject_reason = 'Lạc đề' WHERE id = ?", ra);
        perform(a, get("/api/v1/me/reviews"))
                .andExpect(jsonPath("$.items[0].rejectReason").value("Lạc đề"));
        mvc.perform(get("/api/v1/users/" + a.id() + "/reviews"))
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/users/" + b.id() + "/reviews"))
                .andExpect(jsonPath("$.items[0].id").value(rb))
                .andExpect(jsonPath("$.items[0].rejectReason").isEmpty());
        mvc.perform(get("/api/v1/users/999999999/reviews"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ─── Tiện ích ────────────────────────────────────────────────────────────

    private enum Trust {
        UNVERIFIED,
        /** Đã xác thực, tài khoản mới: trust = base 10. */
        NEW_VERIFIED,
        /** 10 (xác thực) + 18 (tuổi tài khoản trần) + 2 (1 vote hữu ích) = 30 — đúng ngưỡng đăng ngay. */
        TRUSTED
    }

    private record Account(long id, String accessToken) {}

    private Account account(Trust trust) throws Exception {
        String email = "review-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử đánh giá"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        if (trust != Trust.UNVERIFIED) {
            jdbc.update("UPDATE users SET email_verified_at = UTC_TIMESTAMP(6) WHERE id = ?", id);
        }
        if (trust == Trust.TRUSTED) {
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

    /** Địa điểm đã duyệt của một người đề xuất riêng (không phải tác giả review). */
    private long approvedPlace() {
        Long proposer = jdbc.queryForObject("SELECT MIN(id) FROM users", Long.class);
        Long category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'pho-bun'", Long.class);
        String slug = "review-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES (?, ?, 'Quán thử đánh giá', ?, '1 Phố Thử', 'Hà Nội',
                        ST_PointFromText('POINT(21.03 105.85)', 4326), 'APPROVED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, category, proposer, slug);
        Long id = jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    private ResultActions create(
            Account author, long placeId, int rating, String content, LocalDate visitedAt, String ip) throws Exception {
        String json = """
                {"rating": %d, "content": "%s", "visitedAt": "%s"}
                """.formatted(rating, content, visitedAt);
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

    private static AbstractMockHttpServletRequestBuilder<?> patchJson(long reviewId, String json) {
        return patch("/api/v1/reviews/" + reviewId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    private List<ReviewChangedEvent> changes() {
        return events.stream(ReviewChangedEvent.class).toList();
    }

    private static String listOf(long placeId) {
        return "/api/v1/places/" + placeId + "/reviews";
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    private static long idOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static List<Long> reviewIds(MvcResult result) throws Exception {
        List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.items[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
}
