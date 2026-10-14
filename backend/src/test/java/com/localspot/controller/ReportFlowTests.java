package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Báo cáo vi phạm (UC17) và xử lý báo cáo (UC29) qua HTTP trên MySQL thật: luật gửi (email đã xác thực, mô tả theo lý
 * do, chỉ nội dung đang hiện, không tự báo cáo, một lần / đối tượng), hàng đợi gom theo đối tượng FIFO, ẩn / bỏ qua kèm
 * luật vi phạm O9, đối tượng đã xóa (3c), không tự xử lý, activity_log. Như các flow test khác: không @Transactional,
 * mỗi test tạo dữ liệu riêng; hàng đợi dùng chung CSDL nên chỉ khẳng định về các nhóm do test tạo ra.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String REPORTS = "/api/v1/reports";
    private static final String QUEUE = "/api/v1/moderation/reports";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // ─── UC17: gửi báo cáo ──────────────────────────────────────────────────

    @Test
    void memberReportsEachVisibleTargetOnce() throws Exception {
        Account member = account(true);
        Account author = account(true);
        long placeId = place(author, "APPROVED");
        long reviewId = review(placeId, author, "PUBLISHED");
        long commentId = comment(reviewId, author, "VISIBLE");

        report(member, "PLACE", placeId, "WRONG_INFO", "  Quán đã đổi tên thành Phở Mới  ")
                .andExpect(status().isCreated());
        report(member, "REVIEW", reviewId, "FAKE", null).andExpect(status().isCreated());
        report(member, "COMMENT", commentId, "SPAM", "").andExpect(status().isCreated());
        report(member, "USER", author.id(), "OFFENSIVE", null).andExpect(status().isCreated());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT reason, detail, status FROM reports WHERE reporter_id = ? AND target_type = 'PLACE'",
                member.id());
        assertThat(row)
                .containsEntry("reason", "WRONG_INFO")
                .containsEntry("detail", "Quán đã đổi tên thành Phở Mới")
                .containsEntry("status", "OPEN");
        assertThat(jdbc.queryForObject(
                        "SELECT detail FROM reports WHERE reporter_id = ? AND target_type = 'COMMENT'",
                        String.class,
                        member.id()))
                .isNull();

        // U10: một lần / đối tượng — kể cả đổi lý do
        report(member, "REVIEW", reviewId, "SPAM", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_EXISTS"));
    }

    @Test
    void reportNeedsVerifiedEmailAndDetailForVagueReasons() throws Exception {
        Account author = account(true);
        long reviewId = review(place(author, "APPROVED"), author, "PUBLISHED");

        report(account(false), "REVIEW", reviewId, "SPAM", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        mvc.perform(post(REPORTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("REVIEW", reviewId, "SPAM", null)))
                .andExpect(status().isUnauthorized());

        Account member = account(true);
        for (String reason : List.of("WRONG_INFO", "OTHER")) {
            report(member, "REVIEW", reviewId, reason, "   ")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("detail"));
        }
        report(member, "REVIEW", reviewId, "OTHER", "a".repeat(1001))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("detail"));
        report(member, "REVIEW", reviewId, "NOT_A_REASON", null).andExpect(status().isBadRequest());
        assertThat(openReports("REVIEW", reviewId)).isZero();

        report(member, "REVIEW", reviewId, "OTHER", "Nội dung chép từ trang khác")
                .andExpect(status().isCreated());
    }

    @Test
    void onlyPubliclyVisibleTargetsCanBeReported() throws Exception {
        Account member = account(true);
        Account author = account(true);
        long approved = place(author, "APPROVED");

        for (String status : List.of("PENDING", "REJECTED", "HIDDEN")) {
            report(member, "PLACE", place(author, status), "SPAM", null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
            report(member, "REVIEW", review(approved, account(true), status), "SPAM", null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
        }
        // Review đang hiện nhưng trên địa điểm chưa duyệt
        report(member, "REVIEW", review(place(author, "PENDING"), author, "PUBLISHED"), "SPAM", null)
                .andExpect(status().isNotFound());

        long visibleReview = review(approved, author, "PUBLISHED");
        report(member, "COMMENT", comment(visibleReview, author, "HIDDEN"), "SPAM", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
        long hiddenReview = review(approved, account(true), "HIDDEN");
        report(member, "COMMENT", comment(hiddenReview, author, "VISIBLE"), "SPAM", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
        long deletedComment = comment(visibleReview, author, "VISIBLE");
        jdbc.update("UPDATE comments SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deletedComment);
        report(member, "COMMENT", deletedComment, "SPAM", null).andExpect(status().isNotFound());

        report(member, "USER", 999_999_999L, "SPAM", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void cannotReportOwnContentButProposerMayFlagOwnPlace() throws Exception {
        Account author = account(true);
        long placeId = place(author, "APPROVED");
        long reviewId = review(placeId, author, "PUBLISHED");

        report(author, "REVIEW", reviewId, "SPAM", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));
        report(author, "COMMENT", comment(reviewId, author, "VISIBLE"), "SPAM", null)
                .andExpect(status().isConflict());
        report(author, "USER", author.id(), "SPAM", null).andExpect(status().isConflict());
        // Tên / vị trí không ai sửa được (O5, O8) — báo cáo là kênh duy nhất, kể cả với người đề xuất
        report(author, "PLACE", placeId, "WRONG_INFO", "Mình nhập nhầm số nhà").andExpect(status().isCreated());
    }

    // ─── UC29: hàng đợi ─────────────────────────────────────────────────────

    @Test
    void queueGroupsOpenReportsByTargetOldestFirst() throws Exception {
        Account author = account(true);
        Account moderator = account(true, "MODERATOR");
        long placeId = place(author, "APPROVED");
        long reviewId = review(placeId, author, "PUBLISHED");

        report(account(true), "REVIEW", reviewId, "FAKE", null).andExpect(status().isCreated());
        report(account(true), "PLACE", placeId, "SPAM", null).andExpect(status().isCreated());
        report(account(true), "REVIEW", reviewId, "FAKE", null).andExpect(status().isCreated());
        report(account(true), "REVIEW", reviewId, "OFFENSIVE", null).andExpect(status().isCreated());

        // Đi hết hàng đợi từng mục một: cursor không trùng / sót nhóm
        List<Map<String, Object>> groups = new ArrayList<>();
        String cursor = null;
        do {
            AbstractMockHttpServletRequestBuilder<?> request = get(QUEUE).param("limit", "1");
            if (cursor != null) {
                request = request.param("cursor", cursor);
            }
            MvcResult page =
                    perform(moderator, request).andExpect(status().isOk()).andReturn();
            String json = page.getResponse().getContentAsString();
            groups.addAll(JsonPath.read(json, "$.items"));
            cursor = JsonPath.read(json, "$.nextCursor");
        } while (cursor != null);
        List<Map<String, Object>> ours = groups.stream()
                .filter(g -> isGroup(g, "REVIEW", reviewId) || isGroup(g, "PLACE", placeId))
                .toList();
        assertThat(groups).doesNotHaveDuplicates();
        assertThat(ours).extracting(g -> g.get("targetType")).containsExactly("REVIEW", "PLACE");

        Map<String, Object> reviewGroup = ours.getFirst();
        assertThat(reviewGroup)
                .containsEntry("reportCount", 3)
                .containsEntry("targetStatus", "PUBLISHED")
                .containsEntry("reasons", Map.of("FAKE", 2, "OFFENSIVE", 1));
        assertThat((String) reviewGroup.get("targetPreview")).startsWith("Quán thử báo cáo — Nội dung đánh giá");
        assertThat(ours.get(1))
                .containsEntry("targetPreview", "Quán thử báo cáo")
                .containsEntry("reportCount", 1);

        // Lọc theo loại
        String places = perform(
                        moderator, get(QUEUE).param("targetType", "PLACE").param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<String>>read(places, "$.items[*].targetType")).containsOnly("PLACE");
    }

    @Test
    void detailShowsEachReportAndTheResponsibleUser() throws Exception {
        Account author = account(true);
        Account reporter = account(true);
        Account moderator = account(true, "MODERATOR");
        long placeId = place(author, "APPROVED");

        report(reporter, "PLACE", placeId, "WRONG_INFO", "Địa chỉ đúng là số 12")
                .andExpect(status().isCreated());
        report(account(true), "PLACE", placeId, "SPAM", null).andExpect(status().isCreated());

        perform(moderator, get(QUEUE + "/PLACE/" + placeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group.reportCount").value(2))
                .andExpect(jsonPath("$.group.reasons.WRONG_INFO").value(1))
                .andExpect(jsonPath("$.group.targetStatus").value("APPROVED"))
                .andExpect(jsonPath("$.author.id").value(author.id()))
                .andExpect(jsonPath("$.reports[0].reporter.id").value(reporter.id()))
                .andExpect(jsonPath("$.reports[0].reporter.email").doesNotExist())
                .andExpect(jsonPath("$.reports[0].detail").value("Địa chỉ đúng là số 12"))
                .andExpect(jsonPath("$.reports[1].reason").value("SPAM"));

        perform(moderator, get(QUEUE + "/PLACE/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    void onlyReportHandlersSeeTheQueue() throws Exception {
        perform(account(true), get(QUEUE)).andExpect(status().isForbidden());
        perform(account(true, "OWNER"), get(QUEUE)).andExpect(status().isForbidden());
        perform(account(true, "MODERATOR"), get(QUEUE)).andExpect(status().isOk());
        perform(account(true, "ADMIN"), get(QUEUE)).andExpect(status().isOk());
        perform(account(true), decision("REVIEW", 1, "DISMISS", null)).andExpect(status().isForbidden());
    }

    // ─── UC29: quyết định ───────────────────────────────────────────────────

    @Test
    void hidingAReviewRecordsOneViolationAndResolvesEveryReport() throws Exception {
        Account author = account(true);
        Account moderator = account(true, "MODERATOR");
        long placeId = place(author, "APPROVED");
        long reviewId = review(placeId, author, "PUBLISHED");
        report(account(true), "REVIEW", reviewId, "FAKE", null).andExpect(status().isCreated());
        report(account(true), "REVIEW", reviewId, "SPAM", null).andExpect(status().isCreated());
        Long firstReport = jdbc.queryForObject(
                "SELECT MIN(id) FROM reports WHERE target_type = 'REVIEW' AND target_id = ?", Long.class, reviewId);

        perform(moderator, decision("REVIEW", reviewId, "HIDE", null)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT status FROM reviews WHERE id = ?", String.class, reviewId))
                .isEqualTo("HIDDEN");
        assertThat(jdbc.queryForList(
                        "SELECT status FROM reports WHERE target_type = 'REVIEW' AND target_id = ?",
                        String.class,
                        reviewId))
                .containsExactly("RESOLVED", "RESOLVED");
        assertThat(jdbc.queryForList(
                        "SELECT handled_by FROM reports WHERE target_type = 'REVIEW' AND target_id = ?",
                        Long.class,
                        reviewId))
                .containsOnly(moderator.id());

        Map<String, Object> violation = jdbc.queryForMap(
                "SELECT source_type, source_id, points, created_by FROM user_violations WHERE user_id = ?",
                author.id());
        assertThat(violation).containsEntry("source_type", "REPORT_CONFIRMED").containsEntry("points", 25);
        assertThat(((Number) violation.get("created_by")).longValue()).isEqualTo(moderator.id());
        assertThat(((Number) violation.get("source_id")).longValue()).isEqualTo(firstReport);
        // Hết hiệu lực sau 30 ngày (requirements §5.1)
        assertThat(jdbc.queryForObject(
                        "SELECT TIMESTAMPDIFF(SECOND, created_at, expires_at) FROM user_violations WHERE user_id = ?",
                        Long.class,
                        author.id()))
                .isBetween(
                        Duration.ofDays(30).minusSeconds(5).toSeconds(),
                        Duration.ofDays(30).toSeconds());

        Map<String, Object> log = jdbc.queryForMap(
                "SELECT actor_id, target_type, metadata FROM activity_log WHERE action = 'REPORT_HIDE' AND target_id = ?",
                reviewId);
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(moderator.id());
        assertThat(log).containsEntry("target_type", "REVIEW");
        assertThat(JsonPath.<Integer>read((String) log.get("metadata"), "$.reportCount"))
                .isEqualTo(2);
        assertThat(JsonPath.<Boolean>read((String) log.get("metadata"), "$.violation"))
                .isTrue();

        // Không còn công khai; người thứ hai xử lý → 409, không ghi vi phạm thứ hai
        mvc.perform(get("/api/v1/places/" + placeId + "/reviews"))
                .andExpect(jsonPath("$.items").isEmpty());
        perform(account(true, "MODERATOR"), decision("REVIEW", reviewId, "HIDE", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_HANDLED"));
        assertThat(violationsOf(author)).isEqualTo(1);
    }

    @Test
    void hidingAPlacePenalisesTheProposerOnlyForDeliberateAbuse() throws Exception {
        Account proposer = account(true);
        Account owner = account(true);
        Account moderator = account(true, "MODERATOR");

        // WRONG_INFO được xác nhận → ẩn nhưng không ai bị phạt (O9)
        long stale = place(proposer, "APPROVED");
        jdbc.update("UPDATE places SET owner_id = ? WHERE id = ?", owner.id(), stale);
        report(account(true), "PLACE", stale, "WRONG_INFO", "Quán đã đóng cửa").andExpect(status().isCreated());
        report(account(true), "PLACE", stale, "SPAM", null).andExpect(status().isCreated());

        perform(moderator, decision("PLACE", stale, "HIDE", null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("confirmedReason"));
        // Không được "nâng" lên một lý do không ai báo cáo
        perform(moderator, decision("PLACE", stale, "HIDE", "OFFENSIVE"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("confirmedReason"));
        assertThat(openReports("PLACE", stale)).isEqualTo(2);

        perform(moderator, decision("PLACE", stale, "HIDE", "WRONG_INFO")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM places WHERE id = ?", String.class, stale))
                .isEqualTo("HIDDEN");
        assertThat(violationsOf(proposer)).isZero();
        assertThat(violationsOf(owner)).isZero();
        String slug = jdbc.queryForObject("SELECT slug FROM places WHERE id = ?", String.class, stale);
        mvc.perform(get("/api/v1/places/" + slug)).andExpect(status().isNotFound());

        // SPAM được xác nhận → người đề xuất bị phạt, chủ và người đã check-in thì không
        long spam = place(proposer, "APPROVED");
        jdbc.update("UPDATE places SET owner_id = ? WHERE id = ?", owner.id(), spam);
        Account visitor = account(true);
        jdbc.update("""
                INSERT INTO check_ins (user_id, place_id, location, accuracy_m, distance_m, checkin_date, created_at)
                VALUES (?, ?, ST_PointFromText('POINT(21.03 105.85)', 4326), 10, 5, CURRENT_DATE, UTC_TIMESTAMP(6))
                """, visitor.id(), spam);
        report(account(true), "PLACE", spam, "SPAM", null).andExpect(status().isCreated());

        perform(moderator, decision("PLACE", spam, "HIDE", "SPAM")).andExpect(status().isNoContent());
        assertThat(violationsOf(proposer)).isEqualTo(1);
        assertThat(violationsOf(owner)).isZero();
        assertThat(violationsOf(visitor)).isZero();
    }

    @Test
    void hidingACommentOrConfirmingAUserReportRecordsAViolation() throws Exception {
        Account author = account(true);
        Account moderator = account(true, "MODERATOR");
        long reviewId = review(place(account(true), "APPROVED"), account(true), "PUBLISHED");
        long commentId = comment(reviewId, author, "VISIBLE");
        report(account(true), "COMMENT", commentId, "OFFENSIVE", null).andExpect(status().isCreated());

        perform(moderator, decision("COMMENT", commentId, "HIDE", null)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM comments WHERE id = ?", String.class, commentId))
                .isEqualTo("HIDDEN");
        assertThat(violationsOf(author)).isEqualTo(1);

        // Người dùng: không có gì để ẩn — chỉ ghi vi phạm, tài khoản vẫn dùng được (khóa là việc của admin)
        report(account(true), "USER", author.id(), "OFFENSIVE", null).andExpect(status().isCreated());
        perform(moderator, decision("USER", author.id(), "HIDE", null)).andExpect(status().isNoContent());
        assertThat(violationsOf(author)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Timestamp.class, author.id()))
                .isNull();
        assertThat(openReports("USER", author.id())).isZero();
    }

    @Test
    void dismissingLeavesContentAndAuthorUntouched() throws Exception {
        Account author = account(true);
        Account moderator = account(true, "MODERATOR");
        Account reporter = account(true);
        long reviewId = review(place(author, "APPROVED"), author, "PUBLISHED");
        report(reporter, "REVIEW", reviewId, "SPAM", null).andExpect(status().isCreated());

        perform(moderator, decision("REVIEW", reviewId, "DISMISS", null)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT status FROM reviews WHERE id = ?", String.class, reviewId))
                .isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM reports WHERE target_type = 'REVIEW' AND target_id = ?",
                        String.class,
                        reviewId))
                .isEqualTo("DISMISSED");
        assertThat(violationsOf(author)).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT target_type FROM activity_log WHERE action = 'REPORT_DISMISS' AND target_id = ?",
                        String.class,
                        reviewId))
                .isEqualTo("REVIEW");
        // Đã báo cáo rồi thì không báo cáo lại được, kể cả sau khi bị bỏ qua (U10)
        report(reporter, "REVIEW", reviewId, "FAKE", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_EXISTS"));
    }

    @Test
    void reportsOnDeletedContentCloseWithoutAViolation() throws Exception {
        Account author = account(true);
        Account moderator = account(true, "MODERATOR");
        long reviewId = review(place(author, "APPROVED"), author, "PUBLISHED");
        report(account(true), "REVIEW", reviewId, "FAKE", null).andExpect(status().isCreated());

        perform(author, delete("/api/v1/reviews/" + reviewId)).andExpect(status().isNoContent());
        perform(moderator, get(QUEUE + "/REVIEW/" + reviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group.targetStatus").value("DELETED"))
                .andExpect(jsonPath("$.group.targetPreview").isEmpty())
                .andExpect(jsonPath("$.author").isEmpty());

        perform(moderator, decision("REVIEW", reviewId, "HIDE", null)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM reports WHERE target_type = 'REVIEW' AND target_id = ?",
                        String.class,
                        reviewId))
                .isEqualTo("RESOLVED");
        assertThat(violationsOf(author)).isZero();
        String metadata = jdbc.queryForObject(
                "SELECT metadata FROM activity_log WHERE action = 'REPORT_HIDE' AND target_id = ?",
                String.class,
                reviewId);
        assertThat(JsonPath.<Boolean>read(metadata, "$.targetDeleted")).isTrue();
    }

    @Test
    void moderatorCannotHandleReportsAboutTheirOwnContentOrPlace() throws Exception {
        Account moderator = account(true, "MODERATOR");
        long ownReview = review(place(account(true), "APPROVED"), moderator, "PUBLISHED");
        report(account(true), "REVIEW", ownReview, "FAKE", null).andExpect(status().isCreated());
        long ownedPlace = place(account(true), "APPROVED");
        jdbc.update("UPDATE places SET owner_id = ? WHERE id = ?", moderator.id(), ownedPlace);
        report(account(true), "PLACE", ownedPlace, "SPAM", null).andExpect(status().isCreated());

        for (String decision : List.of("HIDE", "DISMISS")) {
            perform(moderator, decision("REVIEW", ownReview, decision, null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));
            perform(moderator, decision("PLACE", ownedPlace, decision, "SPAM"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));
        }
        assertThat(openReports("REVIEW", ownReview)).isEqualTo(1);
        perform(account(true, "MODERATOR"), decision("REVIEW", ownReview, "HIDE", null))
                .andExpect(status().isNoContent());
    }

    // ─── Dữ liệu thử ────────────────────────────────────────────────────────

    private record Account(long id, String accessToken) {}

    private Account account(boolean emailVerified, String... roles) throws Exception {
        String email = "report-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử báo cáo"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        if (emailVerified) {
            jdbc.update("UPDATE users SET email_verified_at = UTC_TIMESTAMP(6) WHERE id = ?", id);
        }
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

    private long place(Account proposer, String status) {
        Long category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'pho-bun'", Long.class);
        String slug = "report-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES (?, ?, 'Quán thử báo cáo', ?, '1 Phố Thử', 'Hà Nội',
                        ST_PointFromText('POINT(21.03 105.85)', 4326), ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, category, proposer.id(), slug, status);
        Long id = jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    /** Review chèn thẳng — không đi qua cổng chống review ảo (đã có test riêng), chỉ cần trạng thái mong muốn. */
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

    private long comment(long reviewId, Account author, String status) {
        jdbc.update("""
                INSERT INTO comments (review_id, user_id, content, status, created_at, updated_at)
                VALUES (?, ?, 'Bình luận thử báo cáo.', ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, reviewId, author.id(), status);
        Long id = jdbc.queryForObject(
                "SELECT MAX(id) FROM comments WHERE review_id = ? AND user_id = ?", Long.class, reviewId, author.id());
        assertThat(id).isNotNull();
        return id;
    }

    private ResultActions report(Account reporter, String type, long targetId, String reason, String detail)
            throws Exception {
        return perform(
                reporter,
                post(REPORTS).contentType(MediaType.APPLICATION_JSON).content(body(type, targetId, reason, detail)));
    }

    private static String body(String type, long targetId, String reason, String detail) {
        return """
                {"targetType": "%s", "targetId": %d, "reason": "%s", "detail": %s}
                """.formatted(type, targetId, reason, detail == null ? "null" : "\"" + detail + "\"");
    }

    private AbstractMockHttpServletRequestBuilder<?> decision(
            String type, long targetId, String decision, String confirmedReason) {
        return post(QUEUE + "/decision").contentType(MediaType.APPLICATION_JSON).content("""
                        {"targetType": "%s", "targetId": %d, "decision": "%s", "confirmedReason": %s}
                        """.formatted(
                        type, targetId, decision, confirmedReason == null ? "null" : "\"" + confirmedReason + "\""));
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    private static boolean isGroup(Map<String, Object> group, String type, long targetId) {
        return group.get("targetType").equals(type) && ((Number) group.get("targetId")).longValue() == targetId;
    }

    private int openReports(String type, long targetId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reports WHERE target_type = ? AND target_id = ? AND status = 'OPEN'",
                Integer.class,
                type,
                targetId);
        return count == null ? 0 : count;
    }

    private int violationsOf(Account user) {
        Integer count =
                jdbc.queryForObject("SELECT COUNT(*) FROM user_violations WHERE user_id = ?", Integer.class, user.id());
        return count == null ? 0 : count;
    }
}
