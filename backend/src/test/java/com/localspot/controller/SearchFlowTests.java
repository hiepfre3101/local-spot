package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.localspot.repository.PlaceFilter;
import com.localspot.search.PlaceSearchIndex;
import com.localspot.search.PlaceSearchQuery;
import com.localspot.search.SearchSort;
import com.localspot.service.PlaceIndexingService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Checklist E5 qua HTTP trên Meilisearch + MySQL + RabbitMQ thật (consumer bật riêng cho lớp này): tìm tiếng Việt không
 * dấu / sai chính tả / synonym (FR-09), bộ lọc + thứ tự + phân trang, đồng bộ index qua hàng đợi khi duyệt / chủ sửa /
 * đổi tên danh mục (UC08 quy tắc nghiệp vụ). Mỗi test tạo danh mục riêng và lọc theo nó — không lẫn tài liệu của test khác
 * trong cùng index.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String SEARCH = "/api/v1/search";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlaceIndexingService indexing;

    @Autowired
    private PlaceSearchIndex index;

    /** Consumer mail cũng bật theo — mail xác thực của tài khoản thử không gửi ra SMTP thật. */
    @MockitoBean
    @SuppressWarnings("unused")
    private JavaMailSender mailSender;

    // ─── Tiếng Việt (FR-09) ──────────────────────────────────────────────────

    @Test
    void matchesVietnameseWithoutDiacriticsWithTyposAndSynonyms() throws Exception {
        Account member = account("USER");
        long category = category(null);
        long pho = place(member, "Phở Thìn Lò Đúc", category, "APPROVED");
        long cafe = place(member, "Cà phê Giảng", category, "APPROVED");
        long bunCha = place(member, "Bún chả Hương Liên", category, "APPROVED");
        long traSua = place(member, "Trà sữa Đông Đô", category, "APPROVED");
        long garden = place(member, "Nhà Xưa", category, "APPROVED", """
                "description": "Quán có sân vườn rộng, yên tĩnh", "tags": ["View đẹp"]""");
        long pending = place(member, "Phở Thìn chi nhánh 2", category, "PENDING");
        indexing.sync(List.of(pho, cafe, bunCha, traSua, garden, pending));

        // Bỏ dấu, đ → d, gõ dấu sai
        assertThat(searchIds("pho thin", category)).first().isEqualTo(pho);
        assertThat(searchIds("phỏ thin", category)).first().isEqualTo(pho);
        assertThat(searchIds("dong do", category)).first().isEqualTo(traSua);
        // Lỗi chính tả, kể cả âm tiết 4 ký tự (ngưỡng chốt 2026-10-08)
        assertThat(searchIds("huong lienn", category)).first().isEqualTo(bunCha);
        assertThat(searchIds("bunn cha", category)).first().isEqualTo(bunCha);
        assertThat(searchIds("phoo thin", category)).first().isEqualTo(pho);
        // Từ chung chung đầu truy vấn bị bỏ — nếu không, "Nhà Xưa" (mô tả "Quán… yên tĩnh", địa chỉ "Phố") khớp đủ cả
        // "quan" / "pho" / "thin" và che mất Phở Thìn
        assertThat(searchIds("quán phở thìn", category)).first().isEqualTo(pho);
        assertThat(searchIds("tiem pho thin", category)).first().isEqualTo(pho);
        // Synonym (search/synonyms.json)
        assertThat(searchIds("cafe", category)).containsExactly(cafe);
        assertThat(searchIds("coffee giang", category)).containsExactly(cafe);
        assertThat(searchIds("milk tea", category)).containsExactly(traSua);
        // Thẻ và mô tả cũng được tìm
        assertThat(searchIds("view dep", category)).containsExactly(garden);
        assertThat(searchIds("san vuon", category)).containsExactly(garden);
        // Đề xuất chưa duyệt không bao giờ vào index
        assertThat(searchIds("chi nhanh", category)).doesNotContain(pending);
    }

    // ─── Bộ lọc, thứ tự, phân trang (FR-10) ─────────────────────────────────

    @Test
    void appliesFiltersSortsAndPagesLikeThePlaceList() throws Exception {
        Account member = account("USER");
        long root = category(null);
        long child = category(root);
        long wifi = amenityId("wifi");
        long parking = amenityId("do-xe-may");
        long a = place(member, "Bánh cuốn Thanh Vân", child, "APPROVED", """
                "priceMin": 30000, "amenityIds": [%d]""".formatted(wifi));
        long b = place(member, "Bánh cuốn Bà Hoành", root, "APPROVED", """
                "priceMin": 80000, "amenityIds": [%d, %d]""".formatted(wifi, parking));
        long c = place(member, "Bánh cuốn Gia An", child, "APPROVED");
        score(a, "4.500", 10);
        score(b, "3.000", 50);
        score(c, "4.000", 5);
        indexing.sync(List.of(a, b, c));

        assertThat(searchIds("banh cuon", root)).containsExactlyInAnyOrder(a, b, c); // gồm danh mục con
        assertThat(searchIds("banh cuon", child)).containsExactlyInAnyOrder(a, c);
        assertThat(searchIds("banh cuon", root, "amenityIds", wifi + "," + parking))
                .containsExactly(b); // đủ tất cả tiện ích
        assertThat(searchIds("banh cuon", root, "priceMax", "50000")).containsExactly(a); // chưa khai giá bị loại
        assertThat(searchIds("banh cuon", root, "minRating", "4")).containsExactlyInAnyOrder(a, c);

        assertThat(searchIds("banh cuon", root, "sort", "SCORE")).containsExactly(a, c, b);
        assertThat(searchIds("banh cuon", root, "sort", "MOST_REVIEWED")).containsExactly(b, a, c);
        assertThat(searchIds("banh cuon", root, "sort", "NEWEST")).containsExactly(c, b, a);

        // Phân trang: từng trang 1 kết quả theo SCORE, không trùng / sót
        List<Long> paged = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletRequestBuilder request = searchRequest("banh cuon", root, "sort", "SCORE", "limit", "1");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            String body = mvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            paged.addAll(ids(body));
            cursor = JsonPath.read(body, "$.nextCursor");
            if (paged.size() == 1) {
                // Cursor gắn với thứ tự — dùng cho thứ tự khác là lỗi của client
                mvc.perform(searchRequest("banh cuon", root, "sort", "NEWEST", "cursor", cursor))
                        .andExpect(status().isUnprocessableContent())
                        .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
            }
        } while (cursor != null);
        assertThat(paged).containsExactly(a, c, b);
    }

    // ─── Đồng bộ index qua hàng đợi ─────────────────────────────────────────

    @Test
    void indexFollowsApprovalOwnerEditsAndCategoryRename() throws Exception {
        Account proposer = account("USER", "OWNER");
        Account moderator = account("USER", "MODERATOR");
        Account admin = account("USER", "ADMIN");
        long category = category(null);
        long placeId = place(proposer, "Quán Mệ Tư", category, "PENDING");

        // Duyệt (FR-35) → sau commit → queue → consumer → index
        perform(
                        moderator,
                        post("/api/v1/moderation/places/" + placeId + "/decision")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\": \"APPROVE\"}"))
                .andExpect(status().isNoContent());
        awaitSearch("me tu", category, placeId);

        // Chủ sửa mô tả (FR-32) → đồng bộ lại
        jdbc.update("UPDATE places SET owner_id = ? WHERE id = ?", proposer.id(), placeId);
        perform(
                        proposer,
                        patch("/api/v1/owner/places/" + placeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"description\": \"Đặc sản cá kho làng Vũ Đại\"}"))
                .andExpect(status().isOk());
        awaitSearch("ca kho vu dai", category, placeId);

        // Đổi tên danh mục → tài liệu của địa điểm thuộc danh mục được đồng bộ lại
        String slug = jdbc.queryForObject("SELECT slug FROM categories WHERE id = ?", String.class, category);
        perform(
                        admin,
                        put("/api/v1/admin/categories/" + category)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\": \"Ẩm thực xứ Nghệ\", \"slug\": \"%s\"}".formatted(slug)))
                .andExpect(status().isOk());
        awaitSearch("xu nghe", category, placeId);
    }

    @Test
    void neverReturnsPlacesThatAreNoLongerApprovedAndRepairsTheIndex() throws Exception {
        Account member = account("USER");
        long category = category(null);
        long hidden = place(member, "Lẩu dê Hùng Râu", category, "APPROVED");
        long deleted = place(member, "Lẩu dê Bảy Món", category, "APPROVED");
        indexing.sync(List.of(hidden, deleted));
        assertThat(searchIds("lau de", category)).containsExactlyInAnyOrder(hidden, deleted);

        // Đổi trạng thái thẳng trong CSDL (không phát sự kiện) — giả lập message đồng bộ bị mất
        jdbc.update("UPDATE places SET status = 'HIDDEN' WHERE id = ?", hidden);
        jdbc.update("UPDATE places SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);
        assertThat(searchIds("lau de", category)).isEmpty(); // lọc lại trong MySQL

        // Id lệch được đẩy đi đồng bộ lại → index tự xóa chúng
        PlaceSearchQuery query = new PlaceSearchQuery(
                "lau de", new PlaceFilter(category, null, null, null, null), SearchSort.RELEVANCE, 0, 10);
        long deadline = System.currentTimeMillis() + 10_000;
        while (!index.search(query).isEmpty()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Index vẫn còn địa điểm không còn APPROVED");
            }
            Thread.sleep(100);
        }
    }

    // ─── Tham số ─────────────────────────────────────────────────────────────

    @Test
    void isPublicAndValidatesParameters() throws Exception {
        mvc.perform(get(SEARCH).param("q", "phở"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(false));
        mvc.perform(get(SEARCH)).andExpect(status().isBadRequest());
        mvc.perform(get(SEARCH).param("q", "  "))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("q"));
        mvc.perform(get(SEARCH).param("q", "a".repeat(101))).andExpect(status().isUnprocessableContent());
        mvc.perform(get(SEARCH).param("q", "pho").param("minRating", "6")).andExpect(status().isUnprocessableContent());
        mvc.perform(get(SEARCH).param("q", "pho").param("limit", "51")).andExpect(status().isUnprocessableContent());
        mvc.perform(get(SEARCH).param("q", "pho").param("sort", "DISTANCE")).andExpect(status().isBadRequest());
        mvc.perform(get(SEARCH).param("q", "pho").param("cursor", "khong-hop-le"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    // ─── Tiện ích ────────────────────────────────────────────────────────────

    private List<Long> searchIds(String q, long categoryId, String... params) throws Exception {
        String body = mvc.perform(searchRequest(q, categoryId, params))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ids(body);
    }

    private static MockHttpServletRequestBuilder searchRequest(String q, long categoryId, String... params) {
        MockHttpServletRequestBuilder request =
                get(SEARCH).param("q", q).param("categoryId", String.valueOf(categoryId));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return request;
    }

    /** Đồng bộ qua hàng đợi chạy bất đồng bộ — chờ tới khi kết quả tìm có địa điểm (tối đa 10 giây). */
    private void awaitSearch(String q, long categoryId, long placeId) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (searchIds(q, categoryId).contains(placeId)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Tìm \"" + q + "\" chưa thấy địa điểm " + placeId);
    }

    private static List<Long> ids(String body) {
        List<Number> ids = JsonPath.read(body, "$.items[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    /** Danh mục riêng của test (gốc, hoặc con của {@code parentId}). */
    private long category(Long parentId) {
        String slug = "tim-kiem-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO categories (parent_id, name, slug, sort_order) VALUES (?, ?, ?, 0)",
                parentId,
                "Danh mục thử " + slug.substring(9, 17),
                slug);
        Long id = jdbc.queryForObject("SELECT id FROM categories WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    private long amenityId(String slug) {
        Long id = jdbc.queryForObject("SELECT id FROM amenities WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    /** Cột dẫn xuất chỉ luồng tính rating được ghi — test đặt thẳng để kiểm tra lọc / xếp theo điểm. */
    private void score(long placeId, String bayesianScore, int reviewCount) {
        jdbc.update(
                "UPDATE places SET bayesian_score = ?, review_count = ? WHERE id = ?",
                new java.math.BigDecimal(bayesianScore),
                reviewCount,
                placeId);
    }

    private long place(Account proposer, String name, long categoryId, String status) throws Exception {
        return place(proposer, name, categoryId, status, null);
    }

    /** Đề xuất qua API (đúng luồng tạo + thẻ), rồi đặt trạng thái trực tiếp. {@code extraJson}: trường thêm vào body. */
    private long place(Account proposer, String name, long categoryId, String status, String extraJson)
            throws Exception {
        String json = """
                {"name": "%s", "categoryId": %d, "address": "1 Phố Thử", "city": "Hà Nội",
                 "location": {"lat": 21.03, "lng": 105.85}%s}
                """.formatted(name, categoryId, extraJson == null ? "" : ", " + extraJson.strip());
        MvcResult result = perform(
                        proposer,
                        multipart("/api/v1/places")
                                .file(new MockMultipartFile(
                                        "place",
                                        "",
                                        MediaType.APPLICATION_JSON_VALUE,
                                        json.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        jdbc.update("UPDATE places SET status = ? WHERE id = ?", status, id);
        return id;
    }

    private record Account(long id, String accessToken) {}

    private Account account(String... roles) throws Exception {
        String email = "search-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử tìm kiếm"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        for (String role : Arrays.asList(roles)) {
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

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }
}
