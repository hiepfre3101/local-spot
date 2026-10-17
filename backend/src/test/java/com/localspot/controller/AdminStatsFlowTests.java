package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * API quản trị và thống kê trên MySQL thật: hàng chờ gộp {@code /moderation/queue}, dashboard {@code /admin/dashboard}
 * (UC33), nhật ký {@code /admin/activity-log} (FR-42), thống kê của chủ {@code /owner/places/{id}/stats} (UC26).
 *
 * <p>CSDL dùng chung với các lớp test khác: số liệu theo khoảng ngày dùng ngày cố định trong quá khứ (2000–2001) mà
 * không lớp nào khác ghi vào; hàng chờ chỉ so <b>thứ tự tương đối</b> của các mục test tự tạo; số việc tồn so độ chênh.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminStatsFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String QUEUE = "/api/v1/moderation/queue";
    private static final String DASHBOARD = "/api/v1/admin/dashboard";
    private static final String ACTIVITY_LOG = "/api/v1/admin/activity-log";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // ─── Hàng chờ gộp ───────────────────────────────────────────────────────

    @Test
    void queueMergesFourTypesOldestFirstWithTitlesAndFlags() throws Exception {
        Account moderator = account("Kiểm duyệt hàng chờ", "MODERATOR");
        Account proposer = account("Người đề xuất hàng chờ");
        Account author = account("Tác giả hàng chờ"); // tài khoản mới → trust < 30
        Account claimant = account("Người yêu cầu hàng chờ");
        Account firstReporter = account("Người báo cáo đầu");
        Account reported = account("Người bị báo cáo hàng chờ");

        long approved = insertPlace(proposer, "APPROVED", "Quán đã duyệt hàng chờ", "2000-01-01 00:00:00");
        long hidden = insertPlace(proposer, "HIDDEN", "Quán đã ẩn hàng chờ", "2000-01-01 00:00:00");
        long place = insertPlace(proposer, "PENDING", "Quán chờ duyệt hàng chờ", "2000-01-01 00:00:03");
        long review = insertReview(
                approved, author, "PENDING", 2, "Quá đông, giá cao hơn mong đợi.", true, "2000-01-01 00:00:01");
        long onHidden = insertReview(
                hidden,
                author,
                "PENDING",
                4,
                "Review trên địa điểm đã ẩn không vào hàng chờ.",
                false,
                "2000-01-01 00:00:00");
        long claim = insertClaim(approved, claimant, "2000-01-01 00:00:01");
        insertReport(firstReporter, "USER", reported.id(), "2000-01-01 00:00:02");
        insertReport(account("Người báo cáo sau"), "USER", reported.id(), "2000-01-01 00:00:05");

        List<Map<String, Object>> items = wholeQueue(moderator, null, 50);

        // Cùng thời điểm gửi: REVIEW trước CLAIM (thứ tự loại); báo cáo xếp theo báo cáo đầu tiên
        List<String> ours = items.stream()
                .map(AdminStatsFlowTests::itemKey)
                .filter(key -> List.of(
                                "REVIEW:" + review,
                                "CLAIM:" + claim,
                                "REPORT:" + reported.id(),
                                "PLACE:" + place,
                                "REVIEW:" + onHidden)
                        .contains(key))
                .toList();
        assertThat(ours)
                .containsExactly("REVIEW:" + review, "CLAIM:" + claim, "REPORT:" + reported.id(), "PLACE:" + place);
        assertPositionsConsecutive(items);

        Map<String, Object> reviewItem = find(items, "REVIEW", review);
        assertThat(reviewItem.get("title")).isEqualTo("Quán đã duyệt hàng chờ — Quá đông, giá cao hơn mong đợi.");
        assertThat(reviewItem.get("submittedBy")).isEqualTo("Tác giả hàng chờ");
        assertThat(reviewItem.get("submittedAt")).isEqualTo("2000-01-01T00:00:01Z");
        assertThat(reviewItem.get("flags"))
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .containsExactly("IP_FLAGGED", "LOW_TRUST");
        assertThat(reviewItem.get("reportCount")).isNull();

        Map<String, Object> claimItem = find(items, "CLAIM", claim);
        assertThat(claimItem.get("title")).isEqualTo("Quán đã duyệt hàng chờ");
        assertThat(claimItem.get("submittedBy")).isEqualTo("Người yêu cầu hàng chờ");
        assertThat(claimItem.get("flags"))
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .isEmpty();

        Map<String, Object> reportItem = find(items, "REPORT", reported.id());
        assertThat(reportItem.get("reportTargetType")).isEqualTo("USER");
        assertThat(reportItem.get("title")).isEqualTo("Người bị báo cáo hàng chờ");
        assertThat(reportItem.get("submittedBy")).isEqualTo("Người báo cáo đầu");
        assertThat(reportItem.get("submittedAt")).isEqualTo("2000-01-01T00:00:02Z");
        assertThat(reportItem.get("reportCount")).isEqualTo(2);

        Map<String, Object> placeItem = find(items, "PLACE", place);
        assertThat(placeItem.get("title")).isEqualTo("Quán chờ duyệt hàng chờ");
        assertThat(placeItem.get("submittedBy")).isEqualTo("Người đề xuất hàng chờ");
    }

    @Test
    void queueCursorWalksWithoutGapsOrDuplicatesAndRenumbersAfterDecisions() throws Exception {
        Account moderator = account("Kiểm duyệt phân trang", "MODERATOR");
        Account proposer = account("Người đề xuất phân trang");
        List<Long> places = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            // Cùng một thời điểm gửi — khóa phụ (loại, id) phải tách được các mục
            places.add(insertPlace(proposer, "PENDING", "Quán phân trang " + i, "2000-01-02 00:00:00"));
        }

        List<Map<String, Object>> byFifty = wholeQueue(moderator, null, 50);
        List<Map<String, Object>> byTwo = wholeQueue(moderator, null, 2);
        assertThat(byTwo.stream().map(AdminStatsFlowTests::itemKey).toList())
                .doesNotHaveDuplicates()
                .containsExactlyElementsOf(
                        byFifty.stream().map(AdminStatsFlowTests::itemKey).toList());
        assertPositionsConsecutive(byTwo);
        List<String> placeKeys = places.stream().map(id -> "PLACE:" + id).toList();
        assertThat(byTwo.stream()
                        .map(AdminStatsFlowTests::itemKey)
                        .filter(placeKeys::contains)
                        .toList())
                .containsExactlyElementsOf(placeKeys);

        // Lấy cursor ngay sau mục thứ hai của test, duyệt mục thứ nhất → trang sau đánh số lùi một
        int secondIndex = indexOf(byFifty, "PLACE", places.get(1));
        String cursorAfterSecond = cursorAfter(moderator, secondIndex + 1);
        long positionOfThird = ((Number) find(byFifty, "PLACE", places.get(2)).get("position")).longValue();
        perform(
                        moderator,
                        post("/api/v1/moderation/places/" + places.get(0) + "/decision")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\": \"APPROVE\"}"))
                .andExpect(status().isNoContent());
        MvcResult next = perform(
                        moderator, get(QUEUE).param("cursor", cursorAfterSecond).param("limit", "1"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(((Number) JsonPath.read(body(next), "$.items[0].id")).longValue())
                .isEqualTo(places.get(2));
        assertThat(((Number) JsonPath.read(body(next), "$.items[0].position")).longValue())
                .isEqualTo(positionOfThird - 1);
    }

    @Test
    void queueShowsOnlyTypesTheCallerMayHandle() throws Exception {
        Account proposer = account("Người đề xuất quyền");
        long approved = insertPlace(proposer, "APPROVED", "Quán quyền hàng chờ", "2000-01-03 00:00:00");
        insertPlace(proposer, "PENDING", "Quán chờ quyền", "2000-01-03 00:00:00");
        long claim = insertClaim(approved, account("Người yêu cầu quyền"), "2000-01-03 00:00:01");

        String role = "TEST_CLAIMS_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO roles (name) VALUES (?)", role);
        jdbc.update("""
                INSERT INTO role_permissions (role_id, permission_id)
                SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'claim:approve' WHERE r.name = ?
                """, role);
        Account claimsOnly = account("Chỉ duyệt sở hữu", role);

        List<Map<String, Object>> items = wholeQueue(claimsOnly, null, 50);
        assertThat(items).extracting(item -> item.get("type")).containsOnly("CLAIM");
        assertThat(items.stream().map(AdminStatsFlowTests::itemKey)).contains("CLAIM:" + claim);
        perform(claimsOnly, get(QUEUE).param("type", "PLACE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        Account moderator = account("Kiểm duyệt lọc loại", "MODERATOR");
        assertThat(wholeQueue(moderator, "CLAIM", 50))
                .extracting(item -> item.get("type"))
                .containsOnly("CLAIM");
        perform(moderator, get(QUEUE).param("cursor", "khong-phai-cursor"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        perform(moderator, get(QUEUE).param("type", "COMMENT")).andExpect(status().isBadRequest());

        perform(account("Thành viên hàng chờ"), get(QUEUE)).andExpect(status().isForbidden());
        perform(account("Chủ quán hàng chờ", "OWNER"), get(QUEUE)).andExpect(status().isForbidden());
        mvc.perform(get(QUEUE)).andExpect(status().isUnauthorized());
    }

    // ─── Dashboard (UC33) ───────────────────────────────────────────────────

    @Test
    void dashboardCountsByVietnamDayComparesPeriodsAndRanksHotPlaces() throws Exception {
        Account admin = account("Quản trị dashboard", "ADMIN");
        // Mốc ngày theo giờ Việt Nam: 17:30 UTC đã là 00:30 hôm sau
        insertUser("2001-02-28 17:30:00"); // 01/03 VN
        insertUser("2001-03-03 16:59:59"); // 03/03 23:59:59 VN
        insertUser("2001-03-03 17:00:00"); // 04/03 VN — ngoài khoảng
        insertUser("2001-02-27 05:00:00"); // kỳ trước (26–28/02)

        Account proposer = account("Người đề xuất dashboard");
        String city = "Thống Kê " + UUID.randomUUID().toString().substring(0, 8);
        long hot = insertPlace(proposer, "APPROVED", "Quán hot nhất", "2001-01-01 00:00:00", city);
        long warm = insertPlace(proposer, "APPROVED", "Quán hot nhì", "2001-01-01 00:00:00", city);
        long hidden = insertPlace(proposer, "HIDDEN", "Quán đã ẩn dashboard", "2001-01-01 00:00:00", city);
        String day2 = "2001-03-02 03:00:00";
        insertReview(hot, account("R1"), "PUBLISHED", 5, "Ngon, phục vụ nhanh, sẽ quay lại.", false, day2);
        insertReview(hot, account("R2"), "PUBLISHED", 4, "Không gian đẹp, giá hợp lý.", false, day2);
        insertReview(hot, account("R3"), "PENDING", 1, "Đang chờ duyệt — không tính vào hot.", false, day2);
        insertReview(warm, account("R4"), "PUBLISHED", 4, "Ổn, sẽ giới thiệu bạn bè.", false, day2);
        long deleted = insertReview(warm, account("R5"), "PUBLISHED", 3, "Review đã xóa sau đó.", false, day2);
        jdbc.update("UPDATE reviews SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);
        insertReview(hidden, account("R6"), "PUBLISHED", 5, "Địa điểm đã ẩn không lên hot.", false, day2);
        insertReport(account("Người báo cáo dashboard"), "PLACE", warm, "2001-03-01 01:00:00");

        perform(admin, get(DASHBOARD).param("from", "2001-03-01").param("to", "2001-03-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2001-03-01"))
                .andExpect(jsonPath("$.to").value("2001-03-03"))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.newUsersByDay.length()").value(3))
                .andExpect(jsonPath("$.newUsersByDay[0].date").value("2001-03-01"))
                .andExpect(jsonPath("$.newUsersByDay[0].count").value(1))
                .andExpect(jsonPath("$.newUsersByDay[1].count").value(0))
                .andExpect(jsonPath("$.newUsersByDay[2].count").value(1))
                // Review gửi: mọi trạng thái, kể cả đã xóa / trên địa điểm đã ẩn
                .andExpect(jsonPath("$.reviewsByDay[0].count").value(0))
                .andExpect(jsonPath("$.reviewsByDay[1].count").value(6))
                .andExpect(jsonPath("$.reviewsByDay[2].count").value(0))
                .andExpect(jsonPath("$.totals.newUsers").value(2))
                .andExpect(jsonPath("$.totals.reviews").value(6))
                .andExpect(jsonPath("$.totals.reports").value(1))
                .andExpect(jsonPath("$.previousTotals.newUsers").value(1))
                .andExpect(jsonPath("$.previousTotals.reviews").value(0))
                // Xếp hạng chỉ tính review đang hiện
                .andExpect(jsonPath("$.reviewsByCity.length()").value(1))
                .andExpect(jsonPath("$.reviewsByCity[0].city").value(city))
                .andExpect(jsonPath("$.reviewsByCity[0].reviewCount").value(3))
                .andExpect(jsonPath("$.hotPlaces.length()").value(2))
                .andExpect(jsonPath("$.hotPlaces[0].place.id").value(hot))
                .andExpect(jsonPath("$.hotPlaces[0].place.name").value("Quán hot nhất"))
                .andExpect(jsonPath("$.hotPlaces[0].place.category.slug").value("pho-bun"))
                .andExpect(jsonPath("$.hotPlaces[0].newReviewCount").value(2))
                .andExpect(jsonPath("$.hotPlaces[1].place.id").value(warm))
                .andExpect(jsonPath("$.hotPlaces[1].newReviewCount").value(1));
    }

    @Test
    void dashboardDefaultsToFourteenDaysAndPendingCountsFollowTheQueue() throws Exception {
        Account admin = account("Quản trị việc tồn", "ADMIN");
        MvcResult before =
                perform(admin, get(DASHBOARD)).andExpect(status().isOk()).andReturn();
        LocalDate today = LocalDate.now(VN);
        assertThat((String) JsonPath.read(body(before), "$.to")).isEqualTo(today.toString());
        assertThat((String) JsonPath.read(body(before), "$.from"))
                .isEqualTo(today.minusDays(13).toString());
        assertThat((Integer) JsonPath.read(body(before), "$.reviewsByDay.length()"))
                .isEqualTo(14);
        int places = JsonPath.read(body(before), "$.pendingCounts.places");
        int claims = JsonPath.read(body(before), "$.pendingCounts.claims");

        Account proposer = account("Người đề xuất việc tồn");
        insertPlace(proposer, "PENDING", "Quán chờ cũ nhất", "1999-12-31 23:59:59");
        long approved = insertPlace(proposer, "APPROVED", "Quán sở hữu việc tồn", "2000-01-01 00:00:00");
        insertClaim(approved, account("Người yêu cầu việc tồn"), "2000-01-04 00:00:00");

        perform(admin, get(DASHBOARD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCounts.places").value(places + 1))
                .andExpect(jsonPath("$.pendingCounts.claims").value(claims + 1))
                .andExpect(jsonPath("$.pendingCounts.oldestSubmittedAt").value("1999-12-31T23:59:59Z"));
    }

    @Test
    void dashboardRejectsBadRangesAndNonAdmins() throws Exception {
        Account admin = account("Quản trị khoảng ngày", "ADMIN");
        perform(admin, get(DASHBOARD).param("from", "2001-03-05").param("to", "2001-03-01"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        perform(admin, get(DASHBOARD).param("from", "2000-01-01").param("to", "2001-01-01"))
                .andExpect(status().isUnprocessableContent()); // 367 ngày
        perform(admin, get(DASHBOARD).param("from", "2000-01-01").param("to", "2000-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUsersByDay.length()").value(366)); // năm nhuận, đúng giới hạn
        perform(admin, get(DASHBOARD).param("from", "01/03/2001")).andExpect(status().isBadRequest());

        perform(account("Kiểm duyệt không xem dashboard", "MODERATOR"), get(DASHBOARD))
                .andExpect(status().isForbidden());
        mvc.perform(get(DASHBOARD)).andExpect(status().isUnauthorized());
    }

    // ─── Nhật ký thao tác (FR-42) ───────────────────────────────────────────

    @Test
    void activityLogListsNewestFirstWithFiltersAndKeepsDeletedActors() throws Exception {
        Account admin = account("Quản trị nhật ký", "ADMIN");
        Account moderator = account("Kiểm duyệt nhật ký", "MODERATOR");
        Account proposer = account("Người đề xuất nhật ký");
        long first = insertPlace(proposer, "PENDING", "Quán nhật ký 1", "2000-01-05 00:00:00");
        long second = insertPlace(proposer, "PENDING", "Quán nhật ký 2", "2000-01-05 00:00:00");
        perform(
                        moderator,
                        post("/api/v1/moderation/places/" + first + "/decision")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\": \"APPROVE\"}"))
                .andExpect(status().isNoContent());
        perform(
                        moderator,
                        post("/api/v1/moderation/places/" + second + "/decision")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\": \"REJECT\", \"reason\": \"Trùng địa điểm đã có.\"}"))
                .andExpect(status().isNoContent());

        perform(admin, get(ACTIVITY_LOG).param("actorId", String.valueOf(moderator.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].action").value("PLACE_REJECT"))
                .andExpect(jsonPath("$.items[0].targetType").value("PLACE"))
                .andExpect(jsonPath("$.items[0].targetId").value(second))
                .andExpect(jsonPath("$.items[0].metadata.reason").value("Trùng địa điểm đã có."))
                .andExpect(jsonPath("$.items[0].actor.id").value(moderator.id()))
                .andExpect(jsonPath("$.items[0].actor.displayName").value("Kiểm duyệt nhật ký"))
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].ipAddress").doesNotExist())
                .andExpect(jsonPath("$.items[1].action").value("PLACE_APPROVE"))
                .andExpect(jsonPath("$.items[1].targetId").value(first))
                .andExpect(jsonPath("$.nextCursor").isEmpty());

        perform(
                        admin,
                        get(ACTIVITY_LOG)
                                .param("actorId", String.valueOf(moderator.id()))
                                .param("action", "PLACE_APPROVE"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].targetId").value(first));

        MvcResult page1 = perform(
                        admin,
                        get(ACTIVITY_LOG)
                                .param("actorId", String.valueOf(moderator.id()))
                                .param("limit", "1"))
                .andExpect(jsonPath("$.items[0].targetId").value(second))
                .andReturn();
        String cursor = JsonPath.read(body(page1), "$.nextCursor");
        perform(
                        admin,
                        get(ACTIVITY_LOG)
                                .param("actorId", String.valueOf(moderator.id()))
                                .param("limit", "1")
                                .param("cursor", cursor))
                .andExpect(jsonPath("$.items[0].targetId").value(first))
                .andExpect(jsonPath("$.nextCursor").isEmpty());

        // Tài khoản người thao tác bị xóa sau đó — nhật ký vẫn còn người thao tác
        jdbc.update("UPDATE users SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", moderator.id());
        perform(admin, get(ACTIVITY_LOG).param("actorId", String.valueOf(moderator.id())))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].actor.id").value(moderator.id()));

        // Không lọc: mới nhất trước trên toàn bảng
        MvcResult all = perform(admin, get(ACTIVITY_LOG).param("limit", "50")).andReturn();
        List<Number> ids = JsonPath.read(body(all), "$.items[*].id");
        assertThat(ids.stream().map(Number::longValue).toList()).isSortedAccordingTo((a, b) -> Long.compare(b, a));

        perform(account("Kiểm duyệt khác", "MODERATOR"), get(ACTIVITY_LOG)).andExpect(status().isForbidden());
    }

    // ─── Thống kê của chủ (UC26) ────────────────────────────────────────────

    @Test
    void ownerSeesDailyViewsCheckInsAndRatingDistribution() throws Exception {
        Account owner = account("Chủ quán thống kê", "OWNER");
        long place = insertPlace(owner, "APPROVED", "Quán thống kê", "2001-01-01 00:00:00");
        jdbc.update(
                "UPDATE places SET owner_id = ?, review_count = 3, bayesian_score = 4.125 WHERE id = ?",
                owner.id(),
                place);
        jdbc.update("INSERT INTO place_views (place_id, view_date, view_count) VALUES (?, '2001-05-01', 5)", place);
        jdbc.update("INSERT INTO place_views (place_id, view_date, view_count) VALUES (?, '2001-05-03', 2)", place);
        jdbc.update("INSERT INTO place_views (place_id, view_date, view_count) VALUES (?, '2001-05-04', 9)", place);
        insertCheckIn(account("Khách check-in 1"), place, "2001-05-02");
        insertCheckIn(account("Khách check-in 2"), place, "2001-05-02");
        insertReview(
                place, account("S1"), "PUBLISHED", 5, "Tuyệt vời, sẽ quay lại lần nữa.", false, "2001-04-01 00:00:00");
        insertReview(place, account("S2"), "PUBLISHED", 4, "Ngon, giá hơi cao một chút.", false, "2001-04-01 00:00:00");
        insertReview(place, account("S3"), "PUBLISHED", 4, "Phục vụ chu đáo, sạch sẽ.", false, "2001-04-01 00:00:00");
        insertReview(
                place, account("S4"), "PENDING", 1, "Chờ duyệt — không vào phân bố sao.", false, "2001-04-01 00:00:00");

        String stats = "/api/v1/owner/places/" + place + "/stats";
        perform(owner, get(stats).param("from", "2001-05-01").param("to", "2001-05-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2001-05-01"))
                .andExpect(jsonPath("$.to").value("2001-05-03"))
                .andExpect(jsonPath("$.viewsByDay.length()").value(3))
                .andExpect(jsonPath("$.viewsByDay[0].count").value(5))
                .andExpect(jsonPath("$.viewsByDay[1].count").value(0))
                .andExpect(jsonPath("$.viewsByDay[2].date").value("2001-05-03"))
                .andExpect(jsonPath("$.viewsByDay[2].count").value(2))
                .andExpect(jsonPath("$.checkinsByDay[0].count").value(0))
                .andExpect(jsonPath("$.checkinsByDay[1].count").value(2))
                .andExpect(jsonPath("$.checkinsByDay[2].count").value(0))
                .andExpect(jsonPath("$.ratingDistribution['1']").value(0))
                .andExpect(jsonPath("$.ratingDistribution['4']").value(2))
                .andExpect(jsonPath("$.ratingDistribution['5']").value(1))
                .andExpect(jsonPath("$.reviewCount").value(3))
                .andExpect(jsonPath("$.bayesianScore").value(4.125));

        // Mặc định 30 ngày đến hôm nay
        perform(owner, get(stats))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value(LocalDate.now(VN).toString()))
                .andExpect(jsonPath("$.viewsByDay.length()").value(30));
        perform(owner, get(stats).param("from", "2001-05-03").param("to", "2001-05-01"))
                .andExpect(status().isUnprocessableContent());

        perform(account("Chủ quán khác", "OWNER"), get(stats)).andExpect(status().isForbidden());
        perform(account("Thành viên xem thống kê"), get(stats)).andExpect(status().isForbidden());
        perform(owner, get("/api/v1/owner/places/999999999/stats")).andExpect(status().isNotFound());
        mvc.perform(get(stats)).andExpect(status().isUnauthorized());
    }

    // ─── Hỗ trợ ─────────────────────────────────────────────────────────────

    private record Account(long id, String accessToken) {}

    /** Đăng ký qua API (role USER), gán thêm role bằng SQL, email đã xác thực, rồi đăng nhập. */
    private Account account(String displayName, String... roles) throws Exception {
        String email = "stats-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"%s"}
                                """.formatted(email, PASSWORD, displayName)))
                .andExpect(status().isCreated());
        long id =
                Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email));
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
        return new Account(id, JsonPath.read(body(login), "$.accessToken"));
    }

    /** Tài khoản chỉ để đếm người dùng mới — thời điểm đăng ký (UTC) ghi thẳng. */
    private void insertUser(String createdAtUtc) {
        jdbc.update("""
                INSERT INTO users (email, password_hash, display_name, created_at, updated_at)
                VALUES (?, 'x', 'Người dùng thống kê', ?, ?)
                """, "stats-" + UUID.randomUUID() + "@localspot.test", createdAtUtc, createdAtUtc);
    }

    private long insertPlace(Account proposer, String status, String name, String createdAtUtc) {
        return insertPlace(proposer, status, name, createdAtUtc, "Hà Nội");
    }

    private long insertPlace(Account proposer, String status, String name, String createdAtUtc, String city) {
        String slug = "stats-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES ((SELECT id FROM categories WHERE slug = 'pho-bun'), ?, ?, ?, '1 Phố Thử', ?,
                        ST_PointFromText('POINT(21.03 105.85)', 4326), ?, ?, ?)
                """, proposer.id(), name, slug, city, status, createdAtUtc, createdAtUtc);
        return Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug));
    }

    private long insertReview(
            long placeId,
            Account author,
            String status,
            int rating,
            String content,
            boolean ipFlagged,
            String createdAtUtc) {
        jdbc.update("""
                INSERT INTO reviews (place_id, user_id, rating, content, visited_at, status, ip_address, ip_prefix,
                                     ip_flagged, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, '2000-01-01', ?, '203.0.113.1', '203.0.113.0/24', ?, 0, ?, ?)
                """, placeId, author.id(), rating, content, status, ipFlagged, createdAtUtc, createdAtUtc);
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT id FROM reviews WHERE place_id = ? AND user_id = ?", Long.class, placeId, author.id()));
    }

    private long insertClaim(long placeId, Account claimant, String createdAtUtc) {
        jdbc.update("""
                INSERT INTO place_claims (place_id, user_id, contact_phone, evidence_keys, status, created_at)
                VALUES (?, ?, '0900000000', JSON_ARRAY(), 'PENDING', ?)
                """, placeId, claimant.id(), createdAtUtc);
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT MAX(id) FROM place_claims WHERE place_id = ? AND user_id = ?",
                Long.class,
                placeId,
                claimant.id()));
    }

    private void insertReport(Account reporter, String targetType, long targetId, String createdAtUtc) {
        jdbc.update("""
                INSERT INTO reports (reporter_id, target_type, target_id, reason, status, version, created_at)
                VALUES (?, ?, ?, 'SPAM', 'OPEN', 0, ?)
                """, reporter.id(), targetType, targetId, createdAtUtc);
    }

    private void insertCheckIn(Account user, long placeId, String vnDate) {
        jdbc.update("""
                INSERT INTO check_ins (user_id, place_id, location, accuracy_m, distance_m, checkin_date, created_at)
                VALUES (?, ?, ST_PointFromText('POINT(21.03 105.85)', 4326), 10, 0, ?, ?)
                """, user.id(), placeId, vnDate, vnDate + " 03:00:00");
    }

    /** Đi hết hàng chờ bằng cursor, gom mọi mục. */
    private List<Map<String, Object>> wholeQueue(Account actor, String type, int limit) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletRequestBuilder request = get(QUEUE).param("limit", String.valueOf(limit));
            if (type != null) {
                request.param("type", type);
            }
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            String page =
                    body(perform(actor, request).andExpect(status().isOk()).andReturn());
            items.addAll(JsonPath.read(page, "$.items"));
            cursor = JsonPath.read(page, "$.nextCursor");
        } while (cursor != null);
        return items;
    }

    /** Cursor đứng ngay sau {@code count} mục đầu hàng chờ. */
    private String cursorAfter(Account actor, int count) throws Exception {
        MvcResult page = perform(actor, get(QUEUE).param("limit", String.valueOf(count)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(body(page), "$.nextCursor");
    }

    private static void assertPositionsConsecutive(List<Map<String, Object>> items) {
        for (int i = 0; i < items.size(); i++) {
            assertThat(((Number) items.get(i).get("position")).longValue()).isEqualTo(i + 1L);
        }
    }

    private static String itemKey(Map<String, Object> item) {
        return item.get("type") + ":" + ((Number) item.get("id")).longValue();
    }

    private static Map<String, Object> find(List<Map<String, Object>> items, String type, long id) {
        return items.get(indexOf(items, type, id));
    }

    private static int indexOf(List<Map<String, Object>> items, String type, long id) {
        for (int i = 0; i < items.size(); i++) {
            if (itemKey(items.get(i)).equals(type + ":" + id)) {
                return i;
            }
        }
        throw new AssertionError("Không thấy " + type + ":" + id + " trong hàng chờ");
    }

    private ResultActions perform(Account actor, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
