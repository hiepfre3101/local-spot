package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.localspot.config.CacheConfig;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
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
 * {@code GET /home} (FR-08) trên MySQL + Redis thật: nổi bật theo điểm Bayesian, mới duyệt, cây danh mục, đánh giá nổi
 * bật; cache dùng chung phải thấy ngay thay đổi đã commit (duyệt địa điểm). Kèm thẻ ({@code tags}) trên thẻ địa điểm của
 * mọi danh sách. CSDL dùng chung với lớp test khác → dữ liệu của test được đặt lên đầu danh sách (điểm tối đa, id mới
 * nhất, lượt hữu ích lớn) thay vì so toàn bộ danh sách.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeFlowTests {

    private static final String PASSWORD = "LocalSpot2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void guestsGetFeaturedLatestCategoriesInOneRequest() throws Exception {
        Account proposer = account();
        long top = insertApprovedPlace(proposer, "5.000", 3);
        insertApprovedPlace(proposer, "0.000", 0); // mới nhất nhưng chưa có review → không vào mục nổi bật
        clearHomeCache(); // dữ liệu chèn bằng SQL không phát sự kiện xóa cache

        MvcResult home = mvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured[0].id").value(top))
                .andExpect(jsonPath("$.categories[?(@.slug == 'quan-an')].children[*].slug")
                        .value(org.hamcrest.Matchers.hasItem("pho-bun")))
                .andReturn();

        List<Number> scores = JsonPath.read(body(home), "$.featured[*].bayesianScore");
        assertThat(scores).hasSizeLessThanOrEqualTo(8);
        assertThat(scores.stream().map(n -> new BigDecimal(n.toString())).toList())
                .isSortedAccordingTo((a, b) -> b.compareTo(a));
        List<Integer> featuredReviewCounts = JsonPath.read(body(home), "$.featured[*].reviewCount");
        assertThat(featuredReviewCounts).allSatisfy(count -> assertThat(count).isPositive());

        List<Number> latestIds = JsonPath.read(body(home), "$.latest[*].id");
        assertThat(latestIds).isNotEmpty().hasSizeLessThanOrEqualTo(8);
        assertThat(latestIds.stream().map(Number::longValue).toList())
                .isSortedAccordingTo((a, b) -> Long.compare(b, a));
        List<String> featuredStatuses = JsonPath.read(body(home), "$.featured[*].status");
        List<String> latestStatuses = JsonPath.read(body(home), "$.latest[*].status");
        assertThat(featuredStatuses).containsOnly("APPROVED");
        assertThat(latestStatuses).containsOnly("APPROVED");
    }

    @Test
    void approvingPlaceShowsUpDespiteCacheWithItsTags() throws Exception {
        Account proposer = account();
        mvc.perform(get("/api/v1/home")).andExpect(status().isOk()); // nạp cache trước
        String city = "Trang Chủ " + UUID.randomUUID().toString().substring(0, 8);

        String slug = JsonPath.read(
                body(perform(proposer, propose("""
                                {"name": "Bánh cuốn Thanh Vân", "categoryId": %d, "address": "12 Hàng Gà",
                                 "city": "%s", "location": {"lat": 21.0362, "lng": 105.8466},
                                 "tags": ["Ăn sáng", "Bình dân", "Có chỗ gửi xe", "Gia truyền"]}
                                """.formatted(categoryId("pho-bun"), city)))
                        .andExpect(status().isCreated())
                        .andReturn()),
                "$.slug");
        long placeId =
                Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug));
        perform(
                        account("MODERATOR"),
                        post("/api/v1/moderation/places/" + placeId + "/decision")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\": \"APPROVE\"}"))
                .andExpect(status().isNoContent());

        // API trả đủ thẻ (theo bảng chữ cái); giao diện tự giới hạn 3
        mvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latest[0].id").value(placeId))
                .andExpect(jsonPath("$.latest[0].tags.length()").value(4))
                .andExpect(jsonPath("$.latest[0].tags[0]").value("Ăn sáng"))
                .andExpect(jsonPath("$.latest[0].tags[3]").value("Gia truyền"));

        // Cùng thẻ ở danh sách /places (dùng chung PlaceSummaries)
        mvc.perform(get("/api/v1/places").param("city", city))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(placeId))
                .andExpect(jsonPath("$.items[0].tags.length()").value(4));
    }

    @Test
    void featuredReviewIsMostHelpfulVisibleReviewFromFourStars() throws Exception {
        Account proposer = account();
        long place = insertApprovedPlace(proposer, "4.500", 3);
        long pendingPlace = insertPlace(proposer, "PENDING", "0.000", 0);
        String longContent = "Bánh cuốn tráng mỏng, nhân thịt mộc nhĩ đậm vừa. ".repeat(10);

        Account author = account();
        long chosen = insertReview(place, author, 5, longContent, 1_000_000);
        insertReview(place, account(), 3, "Ổn nhưng hơi đông, phải chờ khá lâu mới có bàn.", 2_000_000);
        insertReview(pendingPlace, account(), 5, "Địa điểm chưa duyệt không được lên trang chủ.", 3_000_000);
        clearHomeCache();

        MvcResult home = mvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featuredReview.id").value(chosen))
                .andExpect(jsonPath("$.featuredReview.rating").value(5))
                .andExpect(jsonPath("$.featuredReview.helpfulCount").value(1_000_000))
                .andExpect(jsonPath("$.featuredReview.place.id").value(place))
                .andExpect(jsonPath("$.featuredReview.author.displayName").value("Người thử trang chủ"))
                .andReturn();
        String excerpt = JsonPath.read(body(home), "$.featuredReview.excerpt");
        assertThat(excerpt.codePointCount(0, excerpt.length())).isLessThanOrEqualTo(180);
        assertThat(excerpt).startsWith("Bánh cuốn tráng mỏng").endsWith("…");
        assertThat(body(home)).doesNotContain("\"content\"");

        // Tác giả xóa review nổi bật → sự kiện review đổi xóa cache sau commit, trang chủ chọn lại (không xóa cache
        // tay)
        perform(author, delete("/api/v1/reviews/" + chosen)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featuredReview.id").value(org.hamcrest.Matchers.not(chosen)));
    }

    // ─── Hỗ trợ ─────────────────────────────────────────────────────────────

    private record Account(long id, String accessToken) {}

    /** Đăng ký qua API (role USER), gán thêm role bằng SQL, email đã xác thực, rồi đăng nhập. */
    private Account account(String... roles) throws Exception {
        String email = "home-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử trang chủ"}
                                """.formatted(email, PASSWORD)))
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

    private long insertApprovedPlace(Account proposer, String bayesianScore, int reviewCount) {
        return insertPlace(proposer, "APPROVED", bayesianScore, reviewCount);
    }

    /** Điểm ghi thẳng (không qua tính lại) — test chỉ kiểm thứ tự trang chủ, công thức đã có {@code RatingFlowTests}. */
    private long insertPlace(Account proposer, String status, String bayesianScore, int reviewCount) {
        String slug = "home-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    review_count, avg_rating, bayesian_score, created_at, updated_at)
                VALUES (?, ?, 'Quán thử trang chủ', ?, '1 Phố Thử', 'Hà Nội',
                        ST_PointFromText('POINT(21.03 105.85)', 4326), ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, categoryId("pho-bun"), proposer.id(), slug, status, reviewCount, bayesianScore, bayesianScore);
        return Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug));
    }

    private long insertReview(long placeId, Account author, int rating, String content, int helpfulCount) {
        jdbc.update("""
                INSERT INTO reviews (place_id, user_id, rating, content, visited_at, status, helpful_count, ip_address,
                                     ip_prefix, ip_flagged, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_DATE, 'PUBLISHED', ?, '203.0.113.1', '203.0.113.0/24', FALSE, 0,
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, placeId, author.id(), rating, content, helpfulCount);
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT id FROM reviews WHERE place_id = ? AND user_id = ?", Long.class, placeId, author.id()));
    }

    private void clearHomeCache() {
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.HOME)).invalidate();
    }

    private long categoryId(String slug) {
        return Objects.requireNonNull(
                jdbc.queryForObject("SELECT id FROM categories WHERE slug = ?", Long.class, slug));
    }

    private static MockMultipartFile placePart(String json) {
        return new MockMultipartFile(
                "place", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private static AbstractMockHttpServletRequestBuilder<?> propose(String json) {
        return multipart("/api/v1/places").file(placePart(json));
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
