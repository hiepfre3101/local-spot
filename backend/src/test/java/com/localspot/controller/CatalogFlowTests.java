package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Checklist E3 qua HTTP trên MySQL + Redis thật: cây danh mục / tiện ích công khai (có cache — ghi qua API quản trị phải
 * thấy ngay), quản trị viên tạo / sửa / xóa (FR-39, nhật ký FR-42), cây ≤ 2 cấp, slug trùng, xóa chỉ khi không dùng.
 * Mỗi test tạo danh mục / tiện ích với slug ngẫu nhiên — không đụng dữ liệu seed V3.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String ADMIN_CATEGORIES = "/api/v1/admin/categories";
    private static final String ADMIN_AMENITIES = "/api/v1/admin/amenities";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // ─── Đọc công khai ───────────────────────────────────────────────────────

    @Test
    void guestsReadTwoLevelCategoryTreeAndAmenities() throws Exception {
        MvcResult tree = mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn();
        List<String> rootSlugs = JsonPath.read(tree.getResponse().getContentAsString(), "$[*].slug");
        // Gốc theo sort_order của seed; danh mục gốc do test khác tạo (slug ngẫu nhiên) có thể xen vào sau
        assertThat(rootSlugs).containsSubsequence("quan-an", "ca-phe-do-uong", "diem-check-in");
        List<String> foodChildren =
                JsonPath.read(tree.getResponse().getContentAsString(), "$[?(@.slug == 'quan-an')].children[*].slug");
        assertThat(foodChildren).containsSubsequence("pho-bun", "com", "lau-nuong");
        List<List<?>> grandchildren =
                JsonPath.read(tree.getResponse().getContentAsString(), "$[*].children[*].children");
        assertThat(grandchildren).allSatisfy(children -> assertThat(children).isEmpty());

        mvc.perform(get("/api/v1/amenities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("wifi"))
                .andExpect(jsonPath("$[0].name").value("Wi-Fi miễn phí"));
    }

    // ─── Danh mục (quản trị) ────────────────────────────────────────────────

    @Test
    void adminChangesShowUpImmediatelyDespiteCacheAndAreAudited() throws Exception {
        Account admin = account("USER", "ADMIN");
        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk()); // nạp cache trước
        String slug = slug("banh-mi");

        MvcResult created = perform(admin, postJson(ADMIN_CATEGORIES, """
                        {"parentId": %d, "name": "  Bánh mì  ", "slug": "%s", "icon": "bread", "sortOrder": 9}
                        """.formatted(categoryId("quan-an"), slug)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bánh mì"))
                .andExpect(jsonPath("$.icon").value("bread"))
                .andExpect(jsonPath("$.children.length()").value(0))
                .andReturn();
        long id = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        assertThat(childSlugsOf("quan-an")).contains(slug);
        assertThat(lastLog("CATEGORY_CREATE", id)).isEqualTo(admin.id());

        String renamed = slug("banh-mi-que");
        perform(admin, putJson(ADMIN_CATEGORIES + "/" + id, """
                        {"parentId": %d, "name": "Bánh mì que", "slug": "%s", "icon": ""}
                        """.formatted(categoryId("ca-phe-do-uong"), renamed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(renamed))
                .andExpect(jsonPath("$.icon").isEmpty());
        assertThat(childSlugsOf("quan-an")).doesNotContain(slug);
        assertThat(childSlugsOf("ca-phe-do-uong")).contains(renamed);
        assertThat(lastLog("CATEGORY_UPDATE", id)).isEqualTo(admin.id());

        perform(admin, delete(ADMIN_CATEGORIES + "/" + id)).andExpect(status().isNoContent());
        assertThat(childSlugsOf("ca-phe-do-uong")).doesNotContain(renamed);
        assertThat(lastLog("CATEGORY_DELETE", id)).isEqualTo(admin.id());
    }

    @Test
    void treeStaysAtMostTwoLevels() throws Exception {
        Account admin = account("USER", "ADMIN");
        long root = createCategory(admin, null, slug("goc"));
        long child = createCategory(admin, root, slug("con"));
        long otherRoot = createCategory(admin, null, slug("goc-khac"));

        // Cháu của gốc
        expectCategoryError(
                admin, postJson(ADMIN_CATEGORIES, categoryJson(child, slug("chau"))), "CATEGORY_DEPTH_EXCEEDED");
        // Gốc đang có con không chuyển thành con được
        expectCategoryError(
                admin,
                putJson(ADMIN_CATEGORIES + "/" + root, categoryJson(otherRoot, slug("goc"))),
                "CATEGORY_DEPTH_EXCEEDED");
        // Tự làm cha của mình
        expectCategoryError(
                admin,
                putJson(ADMIN_CATEGORIES + "/" + otherRoot, categoryJson(otherRoot, slug("x"))),
                "CATEGORY_DEPTH_EXCEEDED");
        // Cha không tồn tại
        expectCategoryError(
                admin, postJson(ADMIN_CATEGORIES, categoryJson(999_999_999L, slug("mo-coi"))), "VALIDATION_FAILED");

        // Chuyển con sang gốc khác, rồi gốc (hết con) làm con được
        perform(admin, putJson(ADMIN_CATEGORIES + "/" + child, categoryJson(otherRoot, slug("con-moi"))))
                .andExpect(status().isOk());
        perform(admin, putJson(ADMIN_CATEGORIES + "/" + root, categoryJson(otherRoot, slug("goc-thanh-con"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children.length()").value(0));
    }

    @Test
    void slugMustBeUniqueAndWellFormed() throws Exception {
        Account admin = account("USER", "ADMIN");
        long id = createCategory(admin, null, slug("trung"));

        perform(admin, postJson(ADMIN_CATEGORIES, categoryJson(null, "pho-bun")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLUG_TAKEN"))
                .andExpect(jsonPath("$.errors[0].field").value("slug"));
        perform(admin, putJson(ADMIN_CATEGORIES + "/" + id, categoryJson(null, "pho-bun")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLUG_TAKEN"));
        perform(admin, postJson(ADMIN_CATEGORIES, categoryJson(null, "Phở Bún")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("slug"));
        perform(admin, putJson(ADMIN_CATEGORIES + "/999999999", categoryJson(null, slug("khong-co"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void categoryInUseCannotBeDeleted() throws Exception {
        Account admin = account("USER", "ADMIN");
        long root = createCategory(admin, null, slug("co-con"));
        long child = createCategory(admin, root, slug("co-dia-diem"));
        proposePlace(account("USER"), child, List.of());

        perform(admin, delete(ADMIN_CATEGORIES + "/" + root))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        perform(admin, delete(ADMIN_CATEGORIES + "/" + child))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        // Địa điểm đã xóa mềm vẫn giữ danh mục (khóa ngoại) — vẫn không xóa được
        jdbc.update("UPDATE places SET deleted_at = UTC_TIMESTAMP(6) WHERE category_id = ?", child);
        perform(admin, delete(ADMIN_CATEGORIES + "/" + child))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        assertThat(count("categories", child)).isEqualTo(1);
    }

    // ─── Tiện ích (quản trị) ─────────────────────────────────────────────────

    @Test
    void adminManagesAmenitiesAndCannotDeleteOneInUse() throws Exception {
        Account admin = account("USER", "ADMIN");
        mvc.perform(get("/api/v1/amenities")).andExpect(status().isOk()); // nạp cache trước
        String slug = slug("o-cam-sac");

        MvcResult created = perform(admin, postJson(ADMIN_AMENITIES, """
                        {"name": "Ổ cắm sạc", "slug": "%s", "icon": "plug"}
                        """.formatted(slug)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(slug))
                .andReturn();
        long id = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        assertThat(amenitySlugs()).contains(slug);
        assertThat(lastLog("AMENITY_CREATE", id)).isEqualTo(admin.id());

        perform(admin, putJson(ADMIN_AMENITIES + "/" + id, """
                        {"name": "Ổ cắm điện", "slug": "%s"}
                        """.formatted(slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ổ cắm điện"))
                .andExpect(jsonPath("$.icon").isEmpty());
        perform(admin, postJson(ADMIN_AMENITIES, """
                        {"name": "Wi-Fi", "slug": "wifi"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLUG_TAKEN"));

        // Đang gắn với địa điểm → 409 (CSDL sẽ CASCADE âm thầm nếu không chặn)
        long placeId = proposePlace(account("USER"), categoryId("pho-bun"), List.of(id));
        perform(admin, delete(ADMIN_AMENITIES + "/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AMENITY_IN_USE"));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM place_amenity WHERE place_id = ? AND amenity_id = ?",
                        Integer.class,
                        placeId,
                        id))
                .isEqualTo(1);

        jdbc.update("DELETE FROM place_amenity WHERE amenity_id = ?", id);
        perform(admin, delete(ADMIN_AMENITIES + "/" + id)).andExpect(status().isNoContent());
        assertThat(amenitySlugs()).doesNotContain(slug);
        assertThat(lastLog("AMENITY_DELETE", id)).isEqualTo(admin.id());
        perform(admin, delete(ADMIN_AMENITIES + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AMENITY_NOT_FOUND"));
    }

    @Test
    void onlyAdminsManageTheCatalog() throws Exception {
        String body = categoryJson(null, slug("cam"));
        perform(account("USER"), postJson(ADMIN_CATEGORIES, body)).andExpect(status().isForbidden());
        // Kiểm duyệt viên không quản lý danh mục (Q2)
        perform(account("USER", "MODERATOR"), postJson(ADMIN_CATEGORIES, body)).andExpect(status().isForbidden());
        perform(account("USER", "MODERATOR"), delete(ADMIN_AMENITIES + "/1")).andExpect(status().isForbidden());
        mvc.perform(postJson(ADMIN_CATEGORIES, body)).andExpect(status().isUnauthorized());
    }

    // ─── Tiện ích ────────────────────────────────────────────────────────────

    private record Account(long id, String accessToken) {}

    private Account account(String... roles) throws Exception {
        String email = "catalog-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(postJson("/api/v1/auth/register", """
                        {"email":"%s","password":"%s","displayName":"Người thử danh mục"}
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
        MvcResult login = mvc.perform(postJson("/api/v1/auth/login", """
                        {"email":"%s","password":"%s"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new Account(id, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    private static AbstractMockHttpServletRequestBuilder<?> postJson(String url, String body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static AbstractMockHttpServletRequestBuilder<?> putJson(String url, String body) {
        return put(url).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String slug(String base) {
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String categoryJson(Long parentId, String slug) {
        return """
                {"parentId": %s, "name": "Danh mục thử", "slug": "%s"}
                """.formatted(parentId == null ? "null" : parentId, slug);
    }

    private long createCategory(Account admin, Long parentId, String slug) throws Exception {
        MvcResult result = perform(admin, postJson(ADMIN_CATEGORIES, categoryJson(parentId, slug)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void expectCategoryError(Account admin, AbstractMockHttpServletRequestBuilder<?> request, String code)
            throws Exception {
        perform(admin, request)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.errors[0].field").value("parentId"));
    }

    private long proposePlace(Account member, long categoryId, List<Long> amenityIds) throws Exception {
        String json = """
                {"name": "Quán thử danh mục", "categoryId": %d, "address": "1 Phố Huế", "city": "Thử %s",
                 "location": {"lat": 21.0175, "lng": 105.8523}, "amenityIds": %s}
                """.formatted(categoryId, UUID.randomUUID(), amenityIds);
        MvcResult result = perform(
                        member,
                        multipart("/api/v1/places")
                                .file(new MockMultipartFile(
                                        "place",
                                        "",
                                        MediaType.APPLICATION_JSON_VALUE,
                                        json.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    /** Đọc qua API công khai (có cache) — kiểm cache đã bị xóa sau khi ghi. */
    private List<String> childSlugsOf(String rootSlug) throws Exception {
        String json =
                mvc.perform(get("/api/v1/categories")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[?(@.slug == '" + rootSlug + "')].children[*].slug");
    }

    private List<String> amenitySlugs() throws Exception {
        String json =
                mvc.perform(get("/api/v1/amenities")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[*].slug");
    }

    private long categoryId(String slug) {
        Long id = jdbc.queryForObject("SELECT id FROM categories WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    private int count(String table, long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, id);
        return n == null ? 0 : n;
    }

    /** Người thực hiện của dòng nhật ký mới nhất cho thao tác + đối tượng. */
    private Long lastLog(String action, long targetId) {
        return jdbc.queryForObject(
                "SELECT actor_id FROM activity_log WHERE action = ? AND target_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                action,
                targetId);
    }
}
