package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.localspot.entity.User;
import com.localspot.service.ContributionService;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;
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
 * Theo dõi (UC20, FR-28), hồ sơ công khai, điểm đóng góp + huy hiệu + bảng xếp hạng (UC21, FR-29, O3 — chốt 2026-10-12)
 * qua HTTP trên MySQL thật. Mỗi test dùng một thành phố riêng cho bảng xếp hạng → không lẫn dữ liệu của test khác và
 * không dính cache 5 phút.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityFlowTests {

    private static final String PASSWORD = "LocalSpot2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContributionService contributions;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate tx;

    // ─── Theo dõi ───────────────────────────────────────────────────────────

    @Test
    void followIsIdempotentAndShowsOnBothProfiles() throws Exception {
        Account alice = account();
        Account bob = account();

        perform(alice, put("/api/v1/users/" + bob.id() + "/follow")).andExpect(status().isNoContent());
        perform(alice, put("/api/v1/users/" + bob.id() + "/follow")).andExpect(status().isNoContent());

        perform(alice, get("/api/v1/users/" + bob.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.followingCount").value(0))
                .andExpect(jsonPath("$.followedByMe").value(true))
                .andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(get("/api/v1/users/" + bob.id()))
                .andExpect(jsonPath("$.followedByMe").value(false));
        mvc.perform(get("/api/v1/users/" + alice.id()))
                .andExpect(jsonPath("$.followingCount").value(1));

        perform(alice, delete("/api/v1/users/" + bob.id() + "/follow")).andExpect(status().isNoContent());
        perform(alice, delete("/api/v1/users/" + bob.id() + "/follow")).andExpect(status().isNoContent());
        perform(alice, get("/api/v1/users/" + bob.id()))
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.followedByMe").value(false));
    }

    @Test
    void followGates() throws Exception {
        Account alice = account();

        perform(alice, put("/api/v1/users/" + alice.id() + "/follow"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));
        perform(alice, put("/api/v1/users/" + Long.MAX_VALUE + "/follow"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mvc.perform(put("/api/v1/users/" + alice.id() + "/follow")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/" + Long.MAX_VALUE + "/followers")).andExpect(status().isNotFound());
    }

    @Test
    void followListsAreNewestFirstAndPaged() throws Exception {
        Account star = account();
        List<Account> fans = List.of(account(), account(), account());
        for (Account fan : fans) {
            perform(fan, put("/api/v1/users/" + star.id() + "/follow")).andExpect(status().isNoContent());
        }

        MvcResult first = mvc.perform(get("/api/v1/users/" + star.id() + "/followers?limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(fans.get(2).id()))
                .andExpect(jsonPath("$.items[1].id").value(fans.get(1).id()))
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andReturn();
        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.nextCursor");
        mvc.perform(get("/api/v1/users/" + star.id() + "/followers?limit=2&cursor=" + cursor))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(fans.getFirst().id()))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        mvc.perform(get("/api/v1/users/" + fans.getFirst().id() + "/following"))
                .andExpect(jsonPath("$.items[0].id").value(star.id()));
        mvc.perform(get("/api/v1/users/" + star.id() + "/followers?cursor=rac"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    // ─── Điểm đóng góp ──────────────────────────────────────────────────────

    @Test
    void pointsFollowTheFormulaAndOnlyCountVisibleContent() throws Exception {
        Account author = account();
        String city = city();
        long place = place(city, "APPROVED", null);
        long reviewWithPhotos = review(place, author, "PUBLISHED", 0);
        photos(reviewWithPhotos, 2, "READY"); // +4
        photos(reviewWithPhotos, 3, "PROCESSING"); // chưa READY → 0
        long manyPhotos = review(place(city, "APPROVED", null), author, "PUBLISHED", 0);
        photos(manyPhotos, 7, "READY"); // trần 5 ảnh → +10
        review(place(city, "APPROVED", null), author, "PENDING", 0); // chưa hiện → 0
        long voter = account().id();
        vote(reviewWithPhotos, voter, true); // +1
        vote(reviewWithPhotos, account().id(), false); // vote không hợp lệ → 0
        checkIn(author.id(), place, 0); // +1
        place(city, "APPROVED", author.id()); // đề xuất được duyệt +15
        place(city, "PENDING", author.id()); // đề xuất chưa duyệt → 0

        contributions.refresh(author.id());

        // 10 + 4 + 10 + 10 + 1 + 1 + 15
        int expected = 51;
        assertThat(points(author)).isEqualTo(expected);
        mvc.perform(get("/api/v1/users/" + author.id()))
                .andExpect(jsonPath("$.contributionPoints").value(expected))
                .andExpect(jsonPath("$.reviewCount").value(2))
                .andExpect(jsonPath("$.badges[0].code").value("REVIEWER_1"));

        // Địa điểm bị ẩn → điểm của mọi đóng góp gắn với nó mất; huy hiệu đã trao giữ nguyên
        jdbc.update("UPDATE places SET status = 'HIDDEN' WHERE id IN (?, ?)", place, placeOf(manyPhotos));
        contributions.refresh(author.id());
        assertThat(points(author)).isEqualTo(15);
        mvc.perform(get("/api/v1/users/" + author.id()))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.badges[0].code").value("REVIEWER_1"));
    }

    @Test
    void badgesAreAwardedAtThresholdsOnce() throws Exception {
        Account member = account();
        String city = city();
        for (int i = 0; i < 10; i++) {
            long place = place(city, "APPROVED", null);
            review(place, member, "PUBLISHED", 0);
            checkIn(member.id(), place, 0);
        }
        checkIn(member.id(), place(city, "HIDDEN", null), 0); // địa điểm không hiện → không tính

        assertThat(contributions.refresh(member.id())).isEqualTo(3); // REVIEWER_1, REVIEWER_2, EXPLORER
        assertThat(contributions.refresh(member.id())).isZero(); // không trao lại

        mvc.perform(get("/api/v1/users/" + member.id()))
                .andExpect(jsonPath("$.badges.length()").value(3))
                .andExpect(jsonPath("$.badges[*].code")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("REVIEWER_1", "REVIEWER_2", "EXPLORER")))
                .andExpect(jsonPath("$.badges[0].name").isNotEmpty())
                .andExpect(jsonPath("$.badges[0].awardedAt").isNotEmpty());
    }

    @Test
    void savingTheUserThroughTheEntityKeepsThePoints() throws Exception {
        Account member = account();
        review(place(city(), "APPROVED", null), member, "PUBLISHED", 0);

        tx.executeWithoutResult(status -> {
            User user = entityManager.find(User.class, member.id()); // nạp khi điểm = 0
            contributions.refresh(member.id()); // transaction riêng ghi 10
            user.setBio("Giới thiệu mới");
            entityManager.flush(); // UPDATE của entity không được ghi đè điểm về 0
        });

        assertThat(points(member)).isEqualTo(10);
    }

    @Test
    void apiActionsRefreshPointsInTheBackground() throws Exception {
        Account author = account();
        Account voter = trustedAccount();
        String city = city();
        long place = place(city, "APPROVED", null);
        long review = review(place, author, "PUBLISHED", 0);
        contributions.refresh(author.id());
        assertThat(points(author)).isEqualTo(10);

        perform(voter, put("/api/v1/reviews/" + review + "/vote")).andExpect(status().isOk());
        awaitPoints(author, 11);

        perform(
                        author,
                        post("/api/v1/places/" + place + "/check-in")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"lat\":21.03,\"lng\":105.85,\"accuracyM\":10}"))
                .andExpect(status().isCreated());
        awaitPoints(author, 12);

        perform(voter, delete("/api/v1/reviews/" + review + "/vote")).andExpect(status().isOk());
        awaitPoints(author, 11);
    }

    // ─── Bảng xếp hạng ──────────────────────────────────────────────────────

    @Test
    void leaderboardRanksByPeriodAndCity() throws Exception {
        String city = city();
        Account recent = account();
        Account older = account();
        Account tie = account();
        Account locked = account();
        review(place(city, "APPROVED", null), recent, "PUBLISHED", 2); // 10 điểm, 2 ngày trước
        review(place(city, "APPROVED", null), recent, "PUBLISHED", 3); // +10
        review(place(city, "APPROVED", null), older, "PUBLISHED", 20); // 10 điểm, 20 ngày trước
        review(place(city, "APPROVED", null), older, "PUBLISHED", 40); // +10, 40 ngày trước
        review(place(city, "APPROVED", null), older, "PUBLISHED", 45); // +10
        review(place(city, "APPROVED", null), tie, "PUBLISHED", 21); // 10 điểm, 21 ngày trước
        review(place(city, "APPROVED", null), locked, "PUBLISHED", 1);
        jdbc.update("UPDATE users SET locked_until = UTC_TIMESTAMP(6) + INTERVAL 1 DAY WHERE id = ?", locked.id());
        review(place(city(), "APPROVED", null), tie, "PUBLISHED", 1); // thành phố khác → không tính

        // Tuần: chỉ review 2–3 ngày trước
        leaderboard("WEEK", city)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user.id").value(recent.id()))
                .andExpect(jsonPath("$[0].points").value(20))
                .andExpect(jsonPath("$[0].rank").value(1));
        // Tháng (30 ngày trượt): recent 20, older 10, tie 10 → cùng hạng 2, xếp theo id
        leaderboard("MONTH", city)
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].user.id").value(recent.id()))
                .andExpect(jsonPath("$[1].user.id").value(older.id()))
                .andExpect(jsonPath("$[1].rank").value(2))
                .andExpect(jsonPath("$[2].user.id").value(tie.id()))
                .andExpect(jsonPath("$[2].rank").value(2));
        // Toàn thời gian: older 30 đứng đầu; người bị khóa không có mặt
        leaderboard("ALL", city)
                .andExpect(jsonPath("$[0].user.id").value(older.id()))
                .andExpect(jsonPath("$[0].points").value(30))
                .andExpect(jsonPath("$[1].user.id").value(recent.id()))
                .andExpect(jsonPath("$[2].rank").value(3))
                .andExpect(jsonPath("$[?(@.user.id == " + locked.id() + ")]").isEmpty());
        mvc.perform(get("/api/v1/leaderboard?period=ALL&limit=1&city=" + city))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/leaderboard?limit=101")).andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/v1/leaderboard?period=YEAR")).andExpect(status().is4xxClientError());
    }

    // ─── Hỗ trợ ─────────────────────────────────────────────────────────────

    private record Account(long id, String accessToken) {}

    private static String city() {
        return "TP thử " + UUID.randomUUID().toString().substring(0, 8);
    }

    private Account account() throws Exception {
        String email = "community-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử cộng đồng"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        jdbc.update("UPDATE users SET email_verified_at = UTC_TIMESTAMP(6) WHERE id = ?", id);
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new Account(id, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
    }

    /** 10 (xác thực) + 18 (tuổi trần) + 2 (1 vote hữu ích) = 30 — vote của người này được tính. */
    private Account trustedAccount() throws Exception {
        Account account = account();
        jdbc.update(
                "UPDATE users SET created_at = UTC_TIMESTAMP(6) - INTERVAL 200 DAY, helpful_votes_count = 1 WHERE id = ?",
                account.id());
        return account;
    }

    /**
     * {@code proposerId} = đề xuất của người đó (được duyệt → +15). Không truyền: địa điểm nền của test, không có
     * {@code moderated_at} → không ai được điểm đề xuất (tránh người dùng chung làm bẩn bảng xếp hạng).
     */
    private long place(String city, String status, Long proposerId) {
        Long proposer = proposerId != null ? proposerId : jdbc.queryForObject("SELECT MIN(id) FROM users", Long.class);
        Long category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'com'", Long.class);
        String slug = "community-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    moderated_at, created_at, updated_at)
                VALUES (?, ?, 'Quán thử cộng đồng', ?, '1 Phố Thử', ?,
                        ST_PointFromText('POINT(21.03 105.85)', 4326), ?,
                        IF(?, UTC_TIMESTAMP(6), NULL), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, category, proposer, slug, city, status, proposerId != null && !status.equals("PENDING"));
        Long id = jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    private long review(long placeId, Account author, String status, int daysAgo) {
        jdbc.update("""
                INSERT INTO reviews (place_id, user_id, rating, content, visited_at, status, ip_address, ip_prefix,
                                     ip_flagged, version, created_at, updated_at)
                VALUES (?, ?, 4, 'Nội dung đánh giá đủ dài để hợp lệ.', CURRENT_DATE - INTERVAL ? DAY, ?,
                        '203.0.113.1', '203.0.113.0/24', FALSE, 0,
                        UTC_TIMESTAMP(6) - INTERVAL ? DAY, UTC_TIMESTAMP(6) - INTERVAL ? DAY)
                """, placeId, author.id(), daysAgo, status, daysAgo, daysAgo);
        Long id = jdbc.queryForObject(
                "SELECT id FROM reviews WHERE place_id = ? AND user_id = ?", Long.class, placeId, author.id());
        assertThat(id).isNotNull();
        return id;
    }

    private long placeOf(long reviewId) {
        Long id = jdbc.queryForObject("SELECT place_id FROM reviews WHERE id = ?", Long.class, reviewId);
        assertThat(id).isNotNull();
        return id;
    }

    private void photos(long reviewId, int count, String status) {
        Long author = jdbc.queryForObject("SELECT user_id FROM reviews WHERE id = ?", Long.class, reviewId);
        for (int i = 0; i < count; i++) {
            jdbc.update("""
                    INSERT INTO review_photos (review_id, uploaded_by, storage_key, status, sort_order, created_at)
                    VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(6))
                    """, reviewId, author, "reviews/" + reviewId + "/" + UUID.randomUUID(), status, i);
        }
    }

    private void vote(long reviewId, long voterId, boolean counted) {
        jdbc.update(
                "INSERT INTO review_votes (review_id, user_id, counted, created_at) VALUES (?, ?, ?, UTC_TIMESTAMP(6))",
                reviewId,
                voterId,
                counted);
    }

    private void checkIn(long userId, long placeId, int daysAgo) {
        jdbc.update("""
                INSERT INTO check_ins (user_id, place_id, location, accuracy_m, distance_m, checkin_date, created_at)
                VALUES (?, ?, ST_PointFromText('POINT(21.03 105.85)', 4326), 10, 0,
                        CURRENT_DATE - INTERVAL ? DAY, UTC_TIMESTAMP(6) - INTERVAL ? DAY)
                """, userId, placeId, daysAgo, daysAgo);
    }

    private int points(Account account) {
        Integer value =
                jdbc.queryForObject("SELECT contribution_points FROM users WHERE id = ?", Integer.class, account.id());
        assertThat(value).isNotNull();
        return value;
    }

    /** Listener chạy nền sau commit — chờ tối đa 5 giây. */
    private void awaitPoints(Account account, int expected) throws InterruptedException {
        IntSupplier current = () -> points(account);
        long deadline = System.currentTimeMillis() + 5000;
        while (current.getAsInt() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(current.getAsInt()).isEqualTo(expected);
    }

    private ResultActions leaderboard(String period, String city) throws Exception {
        return mvc.perform(get("/api/v1/leaderboard").param("period", period).param("city", city))
                .andExpect(status().isOk());
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }
}
