package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.localspot.service.TestImages;
import com.localspot.storage.InMemoryObjectStorage;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Yêu cầu xác nhận sở hữu (UC23) và duyệt (UC30) qua HTTP trên MySQL thật: luật gửi (email đã xác thực, 1–5 ảnh minh
 * chứng, địa điểm APPROVED chưa có chủ, một yêu cầu đang chờ / người / địa điểm), minh chứng ở kho riêng tư + dọn khi
 * lỗi, hàng chờ FIFO, link tạm, duyệt (chủ + role OWNER có hiệu lực ngay, tự từ chối yêu cầu khác, ẩn review sẵn có của
 * chủ mới), hai duyệt đồng thời, từ chối (không vi phạm, gửi lại được), không tự duyệt, phân quyền. Kho object là
 * {@link InMemoryObjectStorage}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClaimFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String QUEUE = "/api/v1/moderation/claims";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InMemoryObjectStorage storage;

    // ─── UC23: gửi yêu cầu ──────────────────────────────────────────────────

    @Test
    void memberSubmitsClaimWithPrivateEvidence() throws Exception {
        Account member = account(true);
        long placeId = place("APPROVED");

        perform(member, claim(placeId, "  0912 345 678 ", "  Tôi là chủ quán từ 2019  ", 2))
                .andExpect(status().isCreated());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT contact_phone, evidence_note, evidence_keys, status FROM place_claims WHERE user_id = ?",
                member.id());
        assertThat(row)
                .containsEntry("contact_phone", "0912 345 678")
                .containsEntry("evidence_note", "Tôi là chủ quán từ 2019")
                .containsEntry("status", "PENDING");
        List<String> keys = JsonPath.read((String) row.get("evidence_keys"), "$");
        assertThat(keys).hasSize(2).allMatch(key -> key.startsWith("claims/" + placeId + "/"));
        assertThat(keys).allMatch(storage::contains);

        // Một yêu cầu đang chờ / người / địa điểm; claimable tắt với người này
        perform(member, claim(placeId, "0912345678", null, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_ALREADY_PENDING"));
        perform(member, get("/api/v1/places/" + slugOf(placeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimable").value(false));
        // Người khác vẫn gửi được — kiểm duyệt viên so các yêu cầu
        perform(account(true), claim(placeId, "0987654321", null, 1)).andExpect(status().isCreated());
    }

    @Test
    void claimNeedsVerifiedEmailValidEvidenceAndUnownedApprovedPlace() throws Exception {
        long placeId = place("APPROVED");
        Account member = account(true);

        perform(account(false), claim(placeId, "0912345678", null, 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        mvc.perform(claim(placeId, "0912345678", null, 1)).andExpect(status().isUnauthorized());

        perform(member, claim(placeId, "0912345678", null, 0))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("evidence"));
        perform(member, claim(placeId, "0912345678", null, 6))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("evidence"));
        perform(
                        member,
                        claim(placeId, "0912345678", null, 1)
                                .file(new MockMultipartFile(
                                        "evidence", "license.pdf", "application/pdf", "%PDF-1.7".getBytes())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("evidence[1]"));
        perform(member, claim(placeId, "gọi tôi nhé", null, 1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("contactPhone"));

        perform(member, claim(place("PENDING"), "0912345678", null, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
        long owned = place("APPROVED");
        jdbc.update("UPDATE places SET owner_id = ? WHERE id = ?", account(true).id(), owned);
        perform(member, claim(owned, "0912345678", null, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLACE_ALREADY_OWNED"));

        assertThat(claimsOf(member)).isZero();
        assertThat(storage.keysStartingWith("claims/" + placeId + "/")).isEmpty();
        assertThat(storage.keysStartingWith("claims/" + owned + "/")).isEmpty();
    }

    @Test
    void storageOutageRejectsClaimAndLeavesNoFiles() throws Exception {
        Account member = account(true);
        long placeId = place("APPROVED");
        storage.setFailing(true);
        try {
            perform(member, claim(placeId, "0912345678", null, 2)).andExpect(status().isServiceUnavailable());
        } finally {
            storage.setFailing(false);
        }
        assertThat(claimsOf(member)).isZero();
        assertThat(storage.keysStartingWith("claims/" + placeId + "/")).isEmpty();
    }

    // ─── UC30: hàng chờ, màn duyệt, minh chứng ──────────────────────────────

    @Test
    void queueListsPendingClaimsOldestFirstWithCompetingCount() throws Exception {
        Account moderator = account(true, "MODERATOR");
        long placeId = place("APPROVED");
        long first = submit(account(true), placeId);
        long second = submit(account(true), placeId);
        long other = submit(account(true), place("APPROVED"));

        List<Map<String, Object>> items = allQueueItems(moderator);
        List<Long> ids = items.stream().map(ClaimFlowTests::idOf).toList();
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids.stream().filter(id -> id == first || id == second || id == other))
                .containsExactly(first, second, other);
        assertThat(itemOf(items, first))
                .containsEntry("status", "PENDING")
                .containsEntry("contactPhone", "0912345678")
                .containsEntry("evidenceCount", 1)
                .containsEntry("otherPendingClaimCount", 1);
        assertThat(JsonPath.<Integer>read(itemOf(items, first), "$.place.id").longValue())
                .isEqualTo(placeId);
        assertThat(itemOf(items, other)).containsEntry("otherPendingClaimCount", 0);

        perform(moderator, get(QUEUE + "/" + first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.claimant.id").isNumber());
        perform(moderator, get(QUEUE + "/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
    }

    @Test
    void evidenceOpensThroughShortLivedLinkOnly() throws Exception {
        Account moderator = account(true, "MODERATOR");
        long placeId = place("APPROVED");
        Account claimant = account(true);
        perform(claimant, claim(placeId, "0912345678", null, 2)).andExpect(status().isCreated());
        long claimId = latestClaimOf(claimant);
        List<String> keys = JsonPath.read(
                jdbc.queryForObject("SELECT evidence_keys FROM place_claims WHERE id = ?", String.class, claimId), "$");

        // JSON, không 302: SPA gửi token bằng header nên tự gọi API rồi gán url vào ảnh (S1)
        String link = perform(moderator, get(QUEUE + "/" + claimId + "/evidence/1"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.url").value("https://storage.test/" + keys.get(1) + "?X-Amz-Expires=300"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Instant expiresAt = Instant.parse(JsonPath.read(link, "$.expiresAt"));
        assertThat(expiresAt)
                .isBetween(Instant.now().plusSeconds(240), Instant.now().plusSeconds(300));
        perform(moderator, get(QUEUE + "/" + claimId + "/evidence/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
        // Người yêu cầu / thành viên khác không xem được minh chứng
        perform(claimant, get(QUEUE + "/" + claimId + "/evidence/0")).andExpect(status().isForbidden());
    }

    // ─── UC30: quyết định ───────────────────────────────────────────────────

    @Test
    void approvalAssignsOwnerRoleRejectsCompetingClaimsAndHidesOwnersReview() throws Exception {
        Account moderator = account(true, "MODERATOR");
        Account claimant = account(true);
        Account rival = account(true);
        long placeId = place("APPROVED");
        long reviewId = review(placeId, claimant, "PUBLISHED");
        jdbc.update("UPDATE places SET review_count = 1, avg_rating = 4 WHERE id = ?", placeId);
        long claimId = submit(claimant, placeId);
        long rivalClaim = submit(rival, placeId);

        // Trước khi duyệt: chưa có quyền chủ
        perform(claimant, get("/api/v1/owner/places")).andExpect(status().isForbidden());

        perform(moderator, decision(claimId, "APPROVE", null, null)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT owner_id FROM places WHERE id = ?", Long.class, placeId))
                .isEqualTo(claimant.id());
        assertThat(jdbc.queryForMap("SELECT status, reviewed_by FROM place_claims WHERE id = ?", claimId))
                .containsEntry("status", "APPROVED");
        assertThat(jdbc.queryForMap("SELECT status, reject_reason FROM place_claims WHERE id = ?", rivalClaim))
                .containsEntry("status", "REJECTED")
                .containsEntry("reject_reason", "Địa điểm đã được xác nhận cho một chủ khác.");
        assertThat(rolesOf(claimant)).contains("OWNER", "USER");
        // Quyền nạp lại từ CSDL mỗi request → token cũ dùng được ngay
        String owned = perform(claimant, get("/api/v1/owner/places"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(owned, "$[*].id")).contains((int) placeId);

        // Review sẵn có của chủ mới bị ẩn (không vi phạm) và điểm được tính lại
        assertThat(jdbc.queryForObject("SELECT status FROM reviews WHERE id = ?", String.class, reviewId))
                .isEqualTo("HIDDEN");
        assertThat(violationsOf(claimant)).isZero();
        awaitReviewCount(placeId, 0);

        String metadata = jdbc.queryForObject(
                "SELECT metadata FROM activity_log WHERE action = 'CLAIM_APPROVE' AND target_id = ?",
                String.class,
                claimId);
        assertThat(JsonPath.<Integer>read(metadata, "$.autoRejected")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(metadata, "$.hiddenReviewId").longValue())
                .isEqualTo(reviewId);

        perform(account(true, "MODERATOR"), decision(rivalClaim, "APPROVE", null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_ALREADY_MODERATED"));
        perform(rival, claim(placeId, "0912345678", null, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLACE_ALREADY_OWNED"));
    }

    @Test
    void concurrentApprovalsOfCompetingClaimsLeaveOneOwner() throws Exception {
        long placeId = place("APPROVED");
        List<Long> claimIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            claimIds.add(submit(account(true), placeId));
        }
        List<Account> moderators = new ArrayList<>();
        for (int i = 0; i < claimIds.size(); i++) {
            moderators.add(account(true, "MODERATOR"));
        }
        ExecutorService pool = Executors.newFixedThreadPool(claimIds.size());
        try {
            List<Callable<MvcResult>> clicks = new ArrayList<>();
            for (int i = 0; i < claimIds.size(); i++) {
                Account moderator = moderators.get(i);
                long claimId = claimIds.get(i);
                clicks.add(() -> perform(moderator, decision(claimId, "APPROVE", null, null))
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
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM place_claims WHERE place_id = ? AND status = 'APPROVED'",
                        Integer.class,
                        placeId))
                .isEqualTo(1);
        Long owner = jdbc.queryForObject("SELECT owner_id FROM places WHERE id = ?", Long.class, placeId);
        assertThat(jdbc.queryForObject(
                        "SELECT user_id FROM place_claims WHERE place_id = ? AND status = 'APPROVED'",
                        Long.class,
                        placeId))
                .isEqualTo(owner);
    }

    @Test
    void rejectionNeedsReasonRecordsNoViolationAndAllowsResubmission() throws Exception {
        Account moderator = account(true, "MODERATOR");
        Account claimant = account(true);
        long placeId = place("APPROVED");
        long claimId = submit(claimant, placeId);

        perform(moderator, decision(claimId, "REJECT", "   ", null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        perform(moderator, decision(claimId, "REJECT", " Ảnh giấy phép không đọc được ", null))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForMap("SELECT status, reject_reason FROM place_claims WHERE id = ?", claimId))
                .containsEntry("status", "REJECTED")
                .containsEntry("reject_reason", "Ảnh giấy phép không đọc được");
        assertThat(violationsOf(claimant)).isZero();
        assertThat(jdbc.queryForObject("SELECT owner_id FROM places WHERE id = ?", Long.class, placeId))
                .isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.reason')) FROM activity_log"
                                + " WHERE action = 'CLAIM_REJECT' AND target_id = ?",
                        String.class,
                        claimId))
                .isEqualTo("Ảnh giấy phép không đọc được");

        perform(claimant, claim(placeId, "0912345678", "Gửi lại ảnh rõ hơn", 1)).andExpect(status().isCreated());
    }

    @Test
    void moderatorCannotApproveOwnClaimOrStaleVersion() throws Exception {
        Account moderator = account(true, "MODERATOR");
        long own = submit(moderator, place("APPROVED"));
        perform(moderator, decision(own, "APPROVE", null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));

        long claimId = submit(account(true), place("APPROVED"));
        perform(moderator, decision(claimId, "APPROVE", null, 7))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM place_claims WHERE id IN (?, ?) AND status = 'PENDING'",
                        Integer.class,
                        own,
                        claimId))
                .isEqualTo(2);
    }

    @Test
    void onlyClaimApproversMayUseTheQueue() throws Exception {
        long claimId = submit(account(true), place("APPROVED"));
        for (Account actor : List.of(account(true), account(true, "OWNER"))) {
            perform(actor, get(QUEUE)).andExpect(status().isForbidden());
            perform(actor, get(QUEUE + "/" + claimId)).andExpect(status().isForbidden());
            perform(actor, decision(claimId, "APPROVE", null, null)).andExpect(status().isForbidden());
        }
        mvc.perform(get(QUEUE)).andExpect(status().isUnauthorized());
        perform(account(true, "ADMIN"), get(QUEUE)).andExpect(status().isOk());
    }

    // ─── Dữ liệu thử ────────────────────────────────────────────────────────

    private record Account(long id, String accessToken) {}

    private Account account(boolean emailVerified, String... roles) throws Exception {
        String email = "claim-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử sở hữu"}
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

    private long place(String status) throws Exception {
        Account proposer = account(true);
        Long category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'pho-bun'", Long.class);
        String slug = "claim-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES (?, ?, 'Quán thử sở hữu', ?, '1 Phố Thử', 'Hà Nội',
                        ST_PointFromText('POINT(21.03 105.85)', 4326), ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, category, proposer.id(), slug, status);
        Long id = jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    private String slugOf(long placeId) {
        return jdbc.queryForObject("SELECT slug FROM places WHERE id = ?", String.class, placeId);
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

    /** Request gửi yêu cầu với {@code evidenceCount} ảnh JPEG hợp lệ. */
    private static MockMultipartHttpServletRequestBuilder claim(
            long placeId, String phone, String note, int evidenceCount) {
        String json = """
                {"contactPhone": "%s", "note": %s}
                """.formatted(phone, note == null ? "null" : "\"" + note + "\"");
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/places/" + placeId + "/claims");
        request.file(new MockMultipartFile(
                "claim", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < evidenceCount; i++) {
            request.file(new MockMultipartFile("evidence", i + ".jpg", "image/jpeg", TestImages.jpeg(48, 48)));
        }
        return request;
    }

    /** Gửi yêu cầu hợp lệ (1 ảnh) và trả id. */
    private long submit(Account claimant, long placeId) throws Exception {
        perform(claimant, claim(placeId, "0912345678", null, 1)).andExpect(status().isCreated());
        return latestClaimOf(claimant);
    }

    private long latestClaimOf(Account claimant) {
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM place_claims WHERE user_id = ?", Long.class, claimant.id());
        assertThat(id).isNotNull();
        return id;
    }

    private AbstractMockHttpServletRequestBuilder<?> decision(
            long claimId, String decision, String reason, Integer version) {
        return post(QUEUE + "/" + claimId + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decision": "%s", "reason": %s, "version": %s}
                        """.formatted(decision, reason == null ? "null" : "\"" + reason + "\"", version));
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    private List<Map<String, Object>> allQueueItems(Account moderator) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        String cursor = null;
        do {
            AbstractMockHttpServletRequestBuilder<?> request = get(QUEUE).param("limit", "50");
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

    private static long idOf(Map<String, Object> item) {
        return ((Number) item.get("id")).longValue();
    }

    private static Map<String, Object> itemOf(List<Map<String, Object>> items, long claimId) {
        return items.stream().filter(item -> idOf(item) == claimId).findFirst().orElseThrow();
    }

    private int claimsOf(Account user) {
        Integer count =
                jdbc.queryForObject("SELECT COUNT(*) FROM place_claims WHERE user_id = ?", Integer.class, user.id());
        return count == null ? 0 : count;
    }

    private int violationsOf(Account user) {
        Integer count =
                jdbc.queryForObject("SELECT COUNT(*) FROM user_violations WHERE user_id = ?", Integer.class, user.id());
        return count == null ? 0 : count;
    }

    private List<String> rolesOf(Account user) {
        return jdbc.queryForList(
                "SELECT r.name FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ?",
                String.class,
                user.id());
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
