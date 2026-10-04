package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.util.UUID;
import java.util.function.Supplier;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Checklist E1 qua HTTP trên MySQL thật: đề xuất (UC11) → duyệt / từ chối (UC27, ghi activity_log) → công khai; chi
 * tiết theo người xem (FR-13); danh sách + lọc + keyset (FR-10); chủ cập nhật (UC24). Như {@link AdminUserFlowTests}:
 * không @Transactional, mỗi test tạo dữ liệu riêng — địa điểm cô lập bằng tên thành phố ngẫu nhiên.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlaceFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String PLACES = "/api/v1/places";
    private static final String MODERATION = "/api/v1/moderation/places";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // ─── Đề xuất (UC11) ──────────────────────────────────────────────────────

    @Test
    void proposalIsPendingAndVisibleOnlyToProposerAndModerators() throws Exception {
        Account member = account("USER");
        Account stranger = account("USER");
        Account moderator = account("USER", "MODERATOR");
        String city = city();

        MvcResult created = perform(member, propose(placeJson("Phở Thìn Lò Đúc", city, """
                        "description": "  Phở bò tái lăn  ",
                        "priceMin": 50000, "priceMax": 80000,
                        "phone": "024 3821 2709", "website": "https://pho.example.vn",
                        "openingHours": [
                          {"dayOfWeek": 1, "openTime": "06:00", "closeTime": "10:30"},
                          {"dayOfWeek": 1, "openTime": "17:00", "closeTime": "21:00"},
                          {"dayOfWeek": 6, "openTime": "18:00", "closeTime": "02:00"}
                        ],
                        "amenityIds": [%d, %d, %d],
                        "tags": ["Hợp gia đình", "hop gia dinh", "Bình dân"]
                        """.formatted(
                                amenityId("wifi"), amenityId("may-lanh"), amenityId("wifi")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.name").value("Phở Thìn Lò Đúc"))
                .andExpect(jsonPath("$.description").value("Phở bò tái lăn"))
                .andExpect(jsonPath("$.category.slug").value("pho-bun"))
                .andExpect(jsonPath("$.location.lat").value(21.0175))
                .andExpect(jsonPath("$.location.lng").value(105.8523))
                .andExpect(jsonPath("$.openingHours.length()").value(3))
                .andExpect(jsonPath("$.openingHours[0].openTime").value("06:00"))
                .andExpect(jsonPath("$.openingHours[2].closeTime").value("02:00"))
                .andExpect(jsonPath("$.amenities.length()").value(2))
                // "Hợp gia đình" và "hop gia dinh" cùng slug → một thẻ, giữ cách viết đầu tiên
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.tags[0]").value("Bình dân"))
                .andExpect(jsonPath("$.tags[1]").value("Hợp gia đình"))
                .andExpect(jsonPath("$.ratingDistribution['5']").value(0))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.owner").isEmpty())
                .andExpect(jsonPath("$.claimable").value(false))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith(PLACES + "/")))
                .andReturn();
        String slug = read(created, "$.slug");
        assertThat(slug).isEqualTo("pho-thin-lo-duc-" + slugOf(city));

        // Chưa duyệt: khách và thành viên khác không thấy (404 như không tồn tại)
        mvc.perform(get(PLACES + "/" + slug))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
        perform(stranger, get(PLACES + "/" + slug)).andExpect(status().isNotFound());
        perform(member, get(PLACES + "/" + slug)).andExpect(status().isOk());
        perform(moderator, get(PLACES + "/" + slug)).andExpect(status().isOk());
        // Địa điểm chưa duyệt không xuất hiện trong danh sách công khai, không tính lượt xem
        mvc.perform(get(PLACES).param("city", city))
                .andExpect(jsonPath("$.items.length()").value(0));
        assertThat(viewsToday(placeIdOf(slug))).isZero();
    }

    @Test
    void slugIsUniqueEvenForSameNameAndCity() throws Exception {
        Account member = account("USER");
        String city = city();

        String first = read(
                perform(member, propose(placeJson("Bún chả Hương Liên", city, null)))
                        .andReturn(),
                "$.slug");
        String second = read(
                perform(member, propose(placeJson("Bún Chả Hương Liên!", city, null)))
                        .andReturn(),
                "$.slug");
        String third = read(
                perform(member, propose(placeJson("bun cha huong lien", city, null)))
                        .andReturn(),
                "$.slug");

        assertThat(first).isEqualTo("bun-cha-huong-lien-" + slugOf(city));
        assertThat(second).isEqualTo(first + "-2");
        assertThat(third).isEqualTo(first + "-3");
    }

    @Test
    void rejectsInvalidProposals() throws Exception {
        Account member = account("USER");
        String city = city();

        mvc.perform(propose(placeJson("Quán khách", city, null))).andExpect(status().isUnauthorized());
        // Thiếu phần "place" của multipart → request không đọc được
        perform(member, multipart(PLACES).file(new MockMultipartFile("other", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        perform(member, propose("""
                        {"name": "Q", "categoryId": %d, "address": "", "city": "%s",
                         "location": {"lat": 91, "lng": 105.8},
                         "website": "javascript:alert(1)", "phone": "abc",
                         "openingHours": [{"dayOfWeek": 8, "openTime": "25:00", "closeTime": "10:00"}]}
                        """.formatted(categoryId("pho-bun"), city)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "name",
                                "address",
                                "location.lat",
                                "website",
                                "phone",
                                "openingHours[0].dayOfWeek",
                                "openingHours[0].openTime")));

        expectFieldError(member, placeJson("Quán A", city, "\"priceMin\": 90000, \"priceMax\": 10000"), "priceMax");
        expectFieldError(member, placeJson("Quán A", city, "\"amenityIds\": [999999]"), "amenityIds");
        expectFieldError(
                member,
                placeJson(
                        "Quán A",
                        city,
                        "\"openingHours\": [{\"dayOfWeek\": 2, \"openTime\": \"08:00\", \"closeTime\": \"08:00\"}]"),
                "openingHours[0].closeTime");
        expectFieldError(member, placeJson("Quán A", city, "\"tags\": [\"!!!\"]"), "tags");
        expectFieldError(member, """
                        {"name": "Quán A", "categoryId": 999999, "address": "1 Phố Huế", "city": "%s",
                         "location": {"lat": 21.0, "lng": 105.8}}
                        """.formatted(city), "categoryId");
    }

    // ─── Duyệt (UC27) ────────────────────────────────────────────────────────

    @Test
    void approvalPublishesPlaceAndIsAudited() throws Exception {
        Account member = account("USER");
        Account moderator = account("USER", "MODERATOR");
        String city = city();
        String slug = proposeSlug(member, "Cà phê Giảng", city);
        long placeId = placeIdOf(slug);

        perform(moderator, decision(placeId, "{\"decision\": \"APPROVE\", \"version\": 0}"))
                .andExpect(status().isNoContent());

        mvc.perform(get(PLACES + "/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.claimable").value(true))
                .andExpect(jsonPath("$.rejectReason").isEmpty());
        mvc.perform(get(PLACES).param("city", city))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].slug").value(slug));
        Map<String, Object> place =
                jdbc.queryForMap("SELECT status, moderated_by, moderated_at FROM places WHERE id = ?", placeId);
        assertThat(((Number) place.get("moderated_by")).longValue()).isEqualTo(moderator.id());
        assertThat(place.get("moderated_at")).isNotNull();

        Map<String, Object> log = auditRow("PLACE_APPROVE", placeId);
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(moderator.id());
        assertThat(log.get("target_type")).isEqualTo("PLACE");
        assertThat(log.get("ip_address")).isEqualTo("127.0.0.1");

        // Đã xử lý → không quyết định lại (không đảo APPROVED → REJECTED ở đây)
        perform(moderator, decision(placeId, "{\"decision\": \"REJECT\", \"reason\": \"Trùng\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLACE_ALREADY_MODERATED"));
        assertThat(auditCount(placeId)).isEqualTo(1);
    }

    @Test
    void rejectionRequiresReasonShownOnlyToProposerAndModerators() throws Exception {
        Account member = account("USER");
        Account stranger = account("USER");
        Account moderator = account("USER", "MODERATOR");
        String slug = proposeSlug(member, "Quán Ma", city());
        long placeId = placeIdOf(slug);

        perform(moderator, decision(placeId, "{\"decision\": \"REJECT\", \"reason\": \"   \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        assertThat(auditCount(placeId)).isZero();

        perform(moderator, decision(placeId, "{\"decision\": \"REJECT\", \"reason\": \"  Địa chỉ không có thật \"}"))
                .andExpect(status().isNoContent());

        perform(member, get(PLACES + "/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("Địa chỉ không có thật"));
        perform(moderator, get(PLACES + "/" + slug))
                .andExpect(jsonPath("$.rejectReason").isString());
        perform(stranger, get(PLACES + "/" + slug)).andExpect(status().isNotFound());
        assertThat(JsonPath.<String>read(
                        jdbc.queryForObject(
                                "SELECT metadata FROM activity_log WHERE action = 'PLACE_REJECT' AND target_id = ?",
                                String.class,
                                placeId),
                        "$.reason"))
                .isEqualTo("Địa chỉ không có thật");
    }

    @Test
    void moderatorCannotApproveOwnProposal() throws Exception {
        Account moderator = account("USER", "MODERATOR");
        long placeId = placeIdOf(proposeSlug(moderator, "Quán người nhà", city()));

        perform(moderator, decision(placeId, "{\"decision\": \"APPROVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));
        assertThat(statusOf(placeId)).isEqualTo("PENDING");
        assertThat(auditCount(placeId)).isZero();
    }

    @Test
    void staleVersionOrUnknownPlaceIsRejected() throws Exception {
        Account member = account("USER");
        Account moderator = account("USER", "MODERATOR");
        long placeId = placeIdOf(proposeSlug(member, "Quán cũ", city()));

        perform(moderator, decision(placeId, "{\"decision\": \"APPROVE\", \"version\": 7}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        perform(moderator, decision(999_999_999L, "{\"decision\": \"APPROVE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
        // Giá trị decision lạ là lỗi kiểu dữ liệu → 400
        perform(moderator, decision(placeId, "{\"decision\": \"MAYBE\"}")).andExpect(status().isBadRequest());
        assertThat(statusOf(placeId)).isEqualTo("PENDING");
    }

    @Test
    void moderationEndpointsRequirePlaceApprovePermission() throws Exception {
        Account member = account("USER");
        long placeId = placeIdOf(proposeSlug(member, "Quán chờ", city()));

        mvc.perform(get(MODERATION)).andExpect(status().isUnauthorized());
        perform(member, get(MODERATION)).andExpect(status().isForbidden());
        perform(member, decision(placeId, "{\"decision\": \"APPROVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(statusOf(placeId)).isEqualTo("PENDING");
    }

    @Test
    void pendingQueueIsFifoAndHistoryIsNewestFirst() throws Exception {
        Account member = account("USER");
        Account moderator = account("USER", "MODERATOR");
        String city = city();
        long a = placeIdOf(proposeSlug(member, "Quán A", city));
        long b = placeIdOf(proposeSlug(member, "Quán B", city));
        long c = placeIdOf(proposeSlug(member, "Quán C", city));
        perform(moderator, decision(b, "{\"decision\": \"REJECT\", \"reason\": \"Trùng\"}"))
                .andExpect(status().isNoContent());

        List<Long> pending = allPages(moderator, () -> get(MODERATION).param("limit", "50"));
        assertThat(pending).contains(a, c).doesNotContain(b).isSorted();
        assertThat(pending.indexOf(a)).isLessThan(pending.indexOf(c));

        List<Long> rejected = allPages(moderator, () -> get(MODERATION).param("status", "REJECTED"));
        assertThat(rejected).contains(b);
        assertThat(rejected).isSortedAccordingTo((x, y) -> Long.compare(y, x));
    }

    // ─── Chi tiết theo người xem (FR-13) ─────────────────────────────────────

    @Test
    void detailCountsViewsAndReportsViewerSpecificFields() throws Exception {
        Account proposer = account("USER");
        Account viewer = account("USER");
        Account owner = account("USER", "OWNER");
        long placeId = approvedPlace(proposer, "Bánh mì Phượng", city());
        String slug = slugOf(placeId);
        int before = viewsToday(placeId);

        // Review PUBLISHED của người khác vào phân bố sao; review đã xóa mềm của viewer vẫn là myReviewId (D2)
        long published = insertReview(placeId, proposer.id(), 5, "PUBLISHED", false);
        insertReview(placeId, account("USER").id(), 4, "PENDING", false);
        long deleted = insertReview(placeId, viewer.id(), 2, "PUBLISHED", true);

        mvc.perform(get(PLACES + "/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingDistribution['5']").value(1))
                .andExpect(jsonPath("$.ratingDistribution['4']").value(0))
                .andExpect(jsonPath("$.ratingDistribution['2']").value(0))
                .andExpect(jsonPath("$.myReviewId").isEmpty())
                .andExpect(jsonPath("$.claimable").value(true));
        perform(viewer, get(PLACES + "/" + slug))
                .andExpect(jsonPath("$.myReviewId").value(deleted));
        perform(proposer, get(PLACES + "/" + slug))
                .andExpect(jsonPath("$.myReviewId").value(published));

        // Đang có yêu cầu sở hữu chờ duyệt → không hiện nút xác nhận sở hữu nữa
        jdbc.update("""
                INSERT INTO place_claims (place_id, user_id, contact_phone, evidence_keys, status, created_at)
                VALUES (?, ?, '0900000000', JSON_ARRAY(), 'PENDING', NOW(6))
                """, placeId, viewer.id());
        perform(viewer, get(PLACES + "/" + slug))
                .andExpect(jsonPath("$.claimable").value(false));
        assertThat(viewsToday(placeId)).isEqualTo(before + 4);

        // Có chủ → không ai xác nhận sở hữu được; chủ xem trang của mình không tính lượt xem
        jdbc.update("UPDATE places SET owner_id = ? WHERE id = ?", owner.id(), placeId);
        perform(owner, get(PLACES + "/" + slug))
                .andExpect(jsonPath("$.owner.id").value(owner.id()))
                .andExpect(jsonPath("$.owner.email").doesNotExist())
                .andExpect(jsonPath("$.claimable").value(false));
        assertThat(viewsToday(placeId)).isEqualTo(before + 4);
    }

    // ─── Danh sách + lọc (FR-10) ─────────────────────────────────────────────

    @Test
    void listFiltersByCategoryTreeAmenitiesPriceAndRating() throws Exception {
        Account member = account("USER");
        String city = city();
        long wifi = amenityId("wifi");
        long aircon = amenityId("may-lanh");
        long pho = approvedPlace(member, placeJson("Phở", city, """
                "priceMin": 40000, "amenityIds": [%d, %d]""".formatted(wifi, aircon)));
        long cafe = approvedPlace(member, """
                        {"name": "Cà phê", "categoryId": %d, "address": "2 Phố Huế", "city": "%s",
                         "location": {"lat": 21.01, "lng": 105.85}, "priceMin": 120000, "amenityIds": [%d]}
                        """.formatted(categoryId("ca-phe"), city, wifi));
        long noPrice = approvedPlace(member, placeJson("Bún", city, null));
        setScore(pho, "4.200", 30);
        setScore(cafe, "3.100", 5);
        setScore(noPrice, "4.800", 2);

        assertThat(listIds(get(PLACES).param("city", city))).containsExactly(noPrice, pho, cafe);
        // Danh mục gốc "Quán ăn" gồm cả danh mục con "Phở & bún"
        assertThat(listIds(get(PLACES).param("city", city).param("categoryId", categoryId("quan-an") + "")))
                .containsExactly(noPrice, pho);
        assertThat(listIds(get(PLACES).param("city", city).param("amenityIds", wifi + "," + aircon)))
                .containsExactly(pho);
        assertThat(listIds(get(PLACES).param("city", city).param("amenityIds", wifi + "")))
                .containsExactly(pho, cafe);
        // Chưa khai giá bị loại khi lọc giá
        assertThat(listIds(get(PLACES).param("city", city).param("priceMax", "100000")))
                .containsExactly(pho);
        assertThat(listIds(get(PLACES).param("city", city).param("minRating", "4.5")))
                .containsExactly(noPrice);
        assertThat(listIds(get(PLACES).param("city", city).param("sort", "MOST_REVIEWED")))
                .containsExactly(pho, cafe, noPrice);
        assertThat(listIds(get(PLACES).param("city", city).param("sort", "NEWEST")))
                .containsExactly(noPrice, cafe, pho);
        // Tên thành phố so khớp không phân biệt hoa thường / dấu
        assertThat(listIds(get(PLACES).param("city", city.toUpperCase()))).hasSize(3);
    }

    @Test
    void listPagesByKeysetWithoutDuplicatesOnTiedScores() throws Exception {
        Account member = account("USER");
        String city = city();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            long id = approvedPlace(member, placeJson("Quán " + i, city, null));
            setScore(id, i < 3 ? "4.000" : "3.500", i); // 3 quán đồng điểm
            ids.add(id);
        }

        List<Long> seen = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletRequestBuilder request =
                    get(PLACES).param("city", city).param("limit", "2");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            MvcResult page = mvc.perform(request).andExpect(status().isOk()).andReturn();
            seen.addAll(idsOf(page));
            cursor = read(page, "$.nextCursor");
        } while (cursor != null);

        // Điểm giảm dần, đồng điểm thì id giảm dần
        assertThat(seen).containsExactly(ids.get(2), ids.get(1), ids.get(0), ids.get(4), ids.get(3));
    }

    @Test
    void listRejectsBadParameters() throws Exception {
        MvcResult first = mvc.perform(get(PLACES).param("limit", "1")).andReturn();
        String scoreCursor = read(first, "$.nextCursor");

        if (scoreCursor != null) {
            // Cursor của thứ tự SCORE không dùng được cho NEWEST
            mvc.perform(get(PLACES).param("sort", "NEWEST").param("cursor", scoreCursor))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        }
        mvc.perform(get(PLACES).param("cursor", "rac"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mvc.perform(get(PLACES).param("minRating", "6"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("minRating"));
        mvc.perform(get(PLACES).param("sort", "RANDOM")).andExpect(status().isBadRequest());
    }

    @Test
    void myPlacesListsOwnProposalsInEveryStatus() throws Exception {
        Account member = account("USER");
        Account moderator = account("USER", "MODERATOR");
        String city = city();
        long pending = placeIdOf(proposeSlug(member, "Quán chờ", city));
        long rejected = placeIdOf(proposeSlug(member, "Quán bị từ chối", city));
        long approved = approvedPlace(member, "Quán đã duyệt", city);
        perform(moderator, decision(rejected, "{\"decision\": \"REJECT\", \"reason\": \"Trùng\"}"));
        proposeSlug(account("USER"), "Quán người khác", city);

        MvcResult first = perform(member, get("/api/v1/me/places").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.items[1].status").value("REJECTED"))
                .andReturn();
        assertThat(idsOf(first)).containsExactly(approved, rejected);
        String cursor = read(first, "$.nextCursor");
        MvcResult second = perform(member, get("/api/v1/me/places").param("cursor", cursor))
                .andReturn();
        assertThat(idsOf(second)).containsExactly(pending);
        assertThat((Object) read(second, "$.nextCursor")).isNull();
    }

    // ─── Chủ địa điểm cập nhật (UC24) ────────────────────────────────────────

    @Test
    void ownerUpdatesInfoHoursAndAmenities() throws Exception {
        Account proposer = account("USER");
        Account owner = account("USER", "OWNER");
        long placeId = approvedPlace(proposer, placeJson("Lẩu Phan", city(), """
                "description": "Cũ", "phone": "0901234567", "priceMin": 100000, "priceMax": 200000,
                "amenityIds": [%d],
                "openingHours": [{"dayOfWeek": 1, "openTime": "10:00", "closeTime": "22:00"}]""".formatted(amenityId("wifi"))));
        jdbc.update("UPDATE places SET owner_id = ? WHERE id = ?", owner.id(), placeId);
        int version = jdbc.queryForObject("SELECT version FROM places WHERE id = ?", Integer.class, placeId);

        perform(owner, get("/api/v1/owner/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(placeId));

        MvcResult updated = perform(owner, ownerPatch(placeId, """
                        {"description": "Lẩu bò nhúng dấm", "phone": "", "priceMax": 250000,
                         "name": "Không đổi được tên", "categoryId": %d,
                         "amenityIds": [%d, %d],
                         "openingHours": [{"dayOfWeek": 7, "openTime": "09:00", "closeTime": "23:30"}],
                         "version": %d}
                        """.formatted(
                                categoryId("ca-phe"), amenityId("may-lanh"), amenityId("do-xe-may"), version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Lẩu bò nhúng dấm"))
                .andExpect(jsonPath("$.phone").isEmpty())
                .andExpect(jsonPath("$.priceMin").value(100000))
                .andExpect(jsonPath("$.priceMax").value(250000))
                .andExpect(jsonPath("$.name").value("Lẩu Phan"))
                .andExpect(jsonPath("$.category.slug").value("pho-bun"))
                .andExpect(jsonPath("$.amenities.length()").value(2))
                .andExpect(jsonPath("$.openingHours.length()").value(1))
                .andExpect(jsonPath("$.openingHours[0].dayOfWeek").value(7))
                .andExpect(jsonPath("$.version").value(version + 1))
                .andReturn();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM opening_hours WHERE place_id = ?", Integer.class, placeId))
                .isEqualTo(1);

        // Form cũ (version cũ) → 409, không ghi đè
        perform(owner, ownerPatch(placeId, "{\"description\": \"Ghi đè\", \"version\": " + version + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        // Giá mới phải khớp với giá đang lưu
        perform(owner, ownerPatch(placeId, "{\"priceMin\": 300000}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("priceMax"));
        perform(owner, ownerPatch(placeId, "{\"address\": \"  \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("address"));
        assertThat((Integer) JsonPath.read(updated.getResponse().getContentAsString(), "$.version"))
                .isEqualTo(version + 1);
    }

    @Test
    void onlyTheOwnerCanUpdateThePlace() throws Exception {
        Account proposer = account("USER");
        Account owner = account("USER", "OWNER");
        Account otherOwner = account("USER", "OWNER");
        Account moderator = account("USER", "MODERATOR");
        long placeId = approvedPlace(proposer, "Quán có chủ", city());
        jdbc.update("UPDATE places SET owner_id = ? WHERE id = ?", owner.id(), placeId);
        String body = "{\"description\": \"Sửa trộm\"}";

        perform(otherOwner, ownerPatch(placeId, body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // Người đề xuất không phải chủ; kiểm duyệt viên không có đường vượt quyền (D6)
        perform(proposer, ownerPatch(placeId, body)).andExpect(status().isForbidden());
        perform(moderator, ownerPatch(placeId, body)).andExpect(status().isForbidden());
        perform(otherOwner, ownerPatch(999_999_999L, body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT description FROM places WHERE id = ?", String.class, placeId))
                .isNull();
    }

    // ─── Tiện ích ────────────────────────────────────────────────────────────

    private record Account(long id, String email, String accessToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    /** Đăng ký qua API (role USER mặc định), gán thêm role bằng SQL rồi đăng nhập. */
    private Account account(String... roles) throws Exception {
        String email = "place-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
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
        return new Account(id, email, read(login, "$.accessToken"));
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, actor.bearer()));
    }

    /** Thành phố ngẫu nhiên (có dấu) để cô lập dữ liệu của từng test trong CSDL dùng chung. */
    private static String city() {
        return "Thử Nghiệm " + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String slugOf(String city) {
        return com.localspot.service.Slugs.slugify(city, 200);
    }

    /** JSON {@code PlaceCreateRequest} tối thiểu (danh mục Phở & bún) + các trường thêm (nội dung bên trong {@code {}}). */
    private String placeJson(String name, String city, String extraFields) {
        return """
                {"name": "%s", "categoryId": %d, "address": "1 Phố Huế", "city": "%s",
                 "location": {"lat": 21.0175, "lng": 105.8523}%s}
                """.formatted(name, categoryId("pho-bun"), city, extraFields == null ? "" : ", " + extraFields);
    }

    private static MockMultipartHttpServletRequestBuilder propose(String placeJson) {
        return multipart(PLACES)
                .file(new MockMultipartFile(
                        "place", "", MediaType.APPLICATION_JSON_VALUE, placeJson.getBytes(StandardCharsets.UTF_8)));
    }

    private String proposeSlug(Account member, String name, String city) throws Exception {
        return read(
                perform(member, propose(placeJson(name, city, null)))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "$.slug");
    }

    private long approvedPlace(Account proposer, String name, String city) throws Exception {
        return approvedPlace(proposer, placeJson(name, city, null));
    }

    /** Đề xuất rồi duyệt bằng một kiểm duyệt viên khác (không tự duyệt được). */
    private long approvedPlace(Account proposer, String placeJson) throws Exception {
        String slug = read(
                perform(proposer, propose(placeJson))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "$.slug");
        long placeId = placeIdOf(slug);
        perform(account("USER", "MODERATOR"), decision(placeId, "{\"decision\": \"APPROVE\"}"))
                .andExpect(status().isNoContent());
        return placeId;
    }

    private static MockHttpServletRequestBuilder decision(long placeId, String body) {
        return post(MODERATION + "/" + placeId + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static MockHttpServletRequestBuilder ownerPatch(long placeId, String body) {
        return patch("/api/v1/owner/places/" + placeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void expectFieldError(Account member, String placeJson, String field) throws Exception {
        perform(member, propose(placeJson))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value(field));
    }

    private List<Long> listIds(MockHttpServletRequestBuilder request) throws Exception {
        return idsOf(mvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    /** Duyệt hết các trang của một danh sách cursor. */
    private List<Long> allPages(Account actor, Supplier<MockHttpServletRequestBuilder> request) throws Exception {
        List<Long> ids = new ArrayList<>();
        MvcResult page =
                perform(actor, request.get()).andExpect(status().isOk()).andReturn();
        ids.addAll(idsOf(page));
        String cursor = read(page, "$.nextCursor");
        while (cursor != null) {
            page = perform(actor, request.get().param("cursor", cursor)).andReturn();
            ids.addAll(idsOf(page));
            cursor = read(page, "$.nextCursor");
        }
        return ids;
    }

    private static List<Long> idsOf(MvcResult page) throws Exception {
        List<Number> ids = JsonPath.read(page.getResponse().getContentAsString(), "$.items[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private long categoryId(String slug) {
        return jdbc.queryForObject("SELECT id FROM categories WHERE slug = ?", Long.class, slug);
    }

    private long amenityId(String slug) {
        return jdbc.queryForObject("SELECT id FROM amenities WHERE slug = ?", Long.class, slug);
    }

    private long placeIdOf(String slug) {
        return jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
    }

    private String slugOf(long placeId) {
        return jdbc.queryForObject("SELECT slug FROM places WHERE id = ?", String.class, placeId);
    }

    private String statusOf(long placeId) {
        return jdbc.queryForObject("SELECT status FROM places WHERE id = ?", String.class, placeId);
    }

    /** Ghi thẳng cột dẫn xuất (chưa có luồng tính rating ở E1) để thử sắp xếp / lọc theo điểm. */
    private void setScore(long placeId, String bayesianScore, int reviewCount) {
        jdbc.update(
                "UPDATE places SET bayesian_score = ?, review_count = ? WHERE id = ?",
                new java.math.BigDecimal(bayesianScore),
                reviewCount,
                placeId);
    }

    private long insertReview(long placeId, long userId, int rating, String status, boolean deleted) {
        jdbc.update("""
                INSERT INTO reviews (place_id, user_id, rating, content, visited_at, status, ip_address, ip_prefix,
                                     created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, 'Nội dung review dùng cho kiểm thử', CURRENT_DATE, ?, '10.0.0.1', '10.0.0',
                        NOW(6), NOW(6), IF(?, NOW(6), NULL))
                """, placeId, userId, rating, status, deleted);
        return jdbc.queryForObject(
                "SELECT id FROM reviews WHERE place_id = ? AND user_id = ?", Long.class, placeId, userId);
    }

    private int viewsToday(long placeId) {
        Integer views = jdbc.queryForObject(
                "SELECT COALESCE(SUM(view_count), 0) FROM place_views WHERE place_id = ? AND view_date = ?",
                Integer.class,
                placeId,
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        return views == null ? 0 : views;
    }

    private Map<String, Object> auditRow(String action, long placeId) {
        return jdbc.queryForMap(
                "SELECT actor_id, target_type, ip_address FROM activity_log WHERE action = ? AND target_id = ?",
                action,
                placeId);
    }

    private int auditCount(long placeId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_log WHERE target_type = 'PLACE' AND target_id = ?",
                Integer.class,
                placeId);
        return count == null ? 0 : count;
    }
}
