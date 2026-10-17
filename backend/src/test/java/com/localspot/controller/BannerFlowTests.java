package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.localspot.service.TestImages;
import com.localspot.storage.InMemoryObjectStorage;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Ảnh trang chủ (UC34, FR-43) trên MySQL + Redis thật: ba ô cố định, thay ảnh xử lý ngay (3 bản, bỏ EXIF), ảnh cũ xóa
 * sau commit, bật / tắt + mô tả có {@code version}, gỡ ảnh; {@code GET /home} thấy thay đổi ngay dù có cache. Ô dùng
 * chung toàn hệ thống nên mỗi test gỡ hết ảnh trước khi chạy.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BannerFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String BANNERS = "/api/v1/admin/banners";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InMemoryObjectStorage storage;

    private Account admin;

    @BeforeEach
    void removeAllBanners() throws Exception {
        admin = account("ADMIN");
        for (String slot : new String[] {"HERO", "SIGNUP_BAND", "COMMUNITY"}) {
            perform(admin, delete(BANNERS + "/" + slot)); // 204 hoặc 404 — chỉ cần ô trống
        }
        storage.setFailing(false);
    }

    @Test
    void adminReplacesHeroImageAndHomeShowsItDespiteCache() throws Exception {
        perform(admin, get(BANNERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].slot").value("HERO"))
                .andExpect(jsonPath("$[0].image").isEmpty())
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[2].slot").value("COMMUNITY"));
        mvc.perform(get("/api/v1/home")).andExpect(jsonPath("$.banners").isEmpty()); // nạp cache

        MvcResult result = perform(
                        admin,
                        upload("HERO", TestImages.rotatedWithGps(), "photo.jpg")
                                .param("altText", "  Phố cổ Hội An lên đèn  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slot").value("HERO"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.altText").value("Phố cổ Hội An lên đèn"))
                .andExpect(jsonPath("$.image.status").value("READY"))
                .andExpect(jsonPath("$.updatedBy.id").value(admin.id()))
                .andReturn();
        String largeUrl = JsonPath.read(body(result), "$.image.largeUrl");
        assertThat(largeUrl).contains("/banners/").endsWith("-large.jpg");

        // 3 bản trên kho, không còn EXIF (GPS) — ảnh gốc không lưu
        Set<String> keys = storage.keysStartingWith("banners/");
        assertThat(keys).hasSize(3);
        String large =
                keys.stream().filter(k -> k.endsWith("-large.jpg")).findFirst().orElseThrow();
        assertThat(TestImages.hasExif(storage.get(large))).isFalse();
        assertThat(storage.keysStartingWith("incoming/banners/")).isEmpty();

        mvc.perform(get("/api/v1/home"))
                .andExpect(jsonPath("$.banners.length()").value(1))
                .andExpect(jsonPath("$.banners[0].slot").value("HERO"))
                .andExpect(jsonPath("$.banners[0].altText").value("Phố cổ Hội An lên đèn"))
                .andExpect(jsonPath("$.banners[0].image.largeUrl").value(largeUrl));

        long id = ((Number) JsonPath.read(body(result), "$.id")).longValue();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM activity_log WHERE action = 'BANNER_IMAGE_REPLACE' AND target_id = ?"
                                + " AND actor_id = ? AND JSON_EXTRACT(metadata, '$.slot') = 'HERO'",
                        Integer.class,
                        id,
                        admin.id()))
                .isEqualTo(1);
    }

    @Test
    void replacingKeepsSettingsDeletesOldImageAndToggleNeedsVersion() throws Exception {
        perform(admin, upload("SIGNUP_BAND", TestImages.jpeg(640, 480), "a.jpg").param("altText", "Nhóm bạn"))
                .andExpect(status().isOk());
        Set<String> first = storage.keysStartingWith("banners/");

        MvcResult second = perform(admin, upload("SIGNUP_BAND", TestImages.transparentPng(), "b.png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.altText").value("Nhóm bạn")) // không gửi altText → giữ mô tả cũ
                .andReturn();
        Set<String> now = storage.keysStartingWith("banners/");
        assertThat(now).hasSize(3).doesNotContainAnyElementsOf(first);
        int version = JsonPath.read(body(second), "$.version");

        perform(admin, patchJson("SIGNUP_BAND", "{\"active\": false, \"version\": %d}".formatted(version + 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        perform(
                        admin,
                        patchJson(
                                "SIGNUP_BAND",
                                "{\"altText\": \"%s\", \"version\": %d}".formatted("x".repeat(201), version)))
                .andExpect(status().isUnprocessableContent());
        perform(admin, patchJson("SIGNUP_BAND", "{\"active\": false, \"version\": %d}".formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(version + 1));
        mvc.perform(get("/api/v1/home")).andExpect(jsonPath("$.banners").isEmpty());
        assertThat(storage.keysStartingWith("banners/")).isEqualTo(now); // tắt không xóa ảnh

        perform(admin, patchJson("COMMUNITY", "{\"active\": true, \"version\": 0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BANNER_NOT_FOUND"));
    }

    @Test
    void removingImageFallsBackToIllustration() throws Exception {
        perform(admin, upload("COMMUNITY", TestImages.jpeg(800, 600), "c.jpg")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/home")).andExpect(jsonPath("$.banners[0].slot").value("COMMUNITY"));

        perform(admin, delete(BANNERS + "/COMMUNITY")).andExpect(status().isNoContent());
        assertThat(storage.keysStartingWith("banners/")).isEmpty();
        mvc.perform(get("/api/v1/home")).andExpect(jsonPath("$.banners").isEmpty());
        perform(admin, get(BANNERS)).andExpect(jsonPath("$[2].image").isEmpty());
        perform(admin, delete(BANNERS + "/COMMUNITY"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BANNER_NOT_FOUND"));
    }

    @Test
    void rejectsBadImagesAndStorageOutageWithoutLeavingAnything() throws Exception {
        byte[] pdf = "%PDF-1.7 không phải ảnh".getBytes(StandardCharsets.UTF_8);
        perform(admin, upload("HERO", pdf, "giay-phep.jpg"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("image"));
        perform(admin, multipart(HttpMethod.PUT, BANNERS + "/HERO/image"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("image"));
        perform(admin, upload("NOI_KHAC", TestImages.jpeg(64, 64), "x.jpg")).andExpect(status().isBadRequest());

        storage.setFailing(true);
        perform(admin, upload("HERO", TestImages.jpeg(64, 64), "x.jpg")).andExpect(status().isServiceUnavailable());
        storage.setFailing(false);

        assertThat(storage.keysStartingWith("banners/")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM home_banners", Integer.class))
                .isZero();
    }

    @Test
    void onlyAdminsWithBannerPermission() throws Exception {
        perform(account("MODERATOR"), get(BANNERS)).andExpect(status().isForbidden());
        perform(account("OWNER"), upload("HERO", TestImages.jpeg(64, 64), "x.jpg"))
                .andExpect(status().isForbidden());
        perform(account(), delete(BANNERS + "/HERO")).andExpect(status().isForbidden());
        mvc.perform(get(BANNERS)).andExpect(status().isUnauthorized());
        assertThat(storage.keysStartingWith("banners/")).isEmpty();
    }

    // ─── Hỗ trợ ─────────────────────────────────────────────────────────────

    private record Account(long id, String accessToken) {}

    private Account account(String... roles) throws Exception {
        String email = "banner-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử ảnh trang chủ"}
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

    private static AbstractMockHttpServletRequestBuilder<?> upload(String slot, byte[] content, String fileName) {
        String type = fileName.endsWith(".png") ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE;
        return multipart(HttpMethod.PUT, BANNERS + "/" + slot + "/image")
                .file(new MockMultipartFile("image", fileName, type, content));
    }

    private static AbstractMockHttpServletRequestBuilder<?> patchJson(String slot, String json) {
        return patch(BANNERS + "/" + slot)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
