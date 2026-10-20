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
import com.localspot.service.PhotoSize;
import com.localspot.service.PlacePhotoService;
import com.localspot.service.TestImages;
import com.localspot.storage.InMemoryObjectStorage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Checklist E2 qua HTTP + RabbitMQ thật (consumer bật riêng cho lớp này, như {@link MailFlowTests}): upload kèm đề xuất
 * và của chủ → hàng đợi → 3 bản ảnh → READY; ảnh bìa; giới hạn số ảnh; xóa; ảnh hỏng; kho sập. Kho object là
 * {@link InMemoryObjectStorage}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlacePhotoFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String PLACES = "/api/v1/places";
    private static final String PUBLIC_URL = "http://cdn.test/localspot/";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InMemoryObjectStorage storage;

    @Autowired
    private PlacePhotoService photoService;

    /** Consumer mail cũng bật theo — mail xác thực của tài khoản thử không gửi ra SMTP thật. */
    @MockitoBean
    @SuppressWarnings("unused")
    private JavaMailSender mailSender;

    // ─── Đề xuất kèm ảnh ─────────────────────────────────────────────────────

    @Test
    void proposalPhotosAreProcessedInBackgroundAndBecomePublicAfterApproval() throws Exception {
        Account member = account("USER");
        String city = city();

        MvcResult created = perform(
                        member,
                        propose(placeJson("Cà phê Ảnh", city))
                                .file(photo("goc.jpg", TestImages.rotatedWithGps()))
                                .file(photo("nen.png", TestImages.transparentPng())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andExpect(jsonPath("$.photos[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$.photos[0].thumbUrl").isEmpty())
                .andExpect(jsonPath("$.coverPhoto").isEmpty())
                .andReturn();
        String slug = read(created, "$.slug");
        long placeId = placeIdOf(slug);
        List<Long> photoIds = photoIdsOf(placeId);
        awaitStatus(photoIds, "READY");

        // Bìa = ảnh đầu tiên; URL suy ra từ storage_key; ảnh gốc (còn GPS) đã bị xóa khỏi kho
        String storageKey = storageKeyOf(photoIds.getFirst());
        assertThat(storageKey).startsWith("places/" + placeId + "/");
        perform(member, get(PLACES + "/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverPhoto.id").value(photoIds.getFirst()))
                .andExpect(jsonPath("$.coverPhoto.thumbUrl").value(PUBLIC_URL + PhotoSize.THUMB.keyOf(storageKey)))
                .andExpect(jsonPath("$.photos[0].largeUrl").value(PUBLIC_URL + PhotoSize.LARGE.keyOf(storageKey)))
                .andExpect(jsonPath("$.photos[1].status").value("READY"));
        for (PhotoSize size : PhotoSize.values()) {
            assertThat(storage.contains(size.keyOf(storageKey))).isTrue();
            assertThat(TestImages.hasExif(storage.get(size.keyOf(storageKey)))).isFalse();
        }
        assertThat(storage.keysStartingWith("incoming/places/" + placeId + "/")).isEmpty();
        // Kích thước bản LARGE sau khi xoay theo EXIF (600 × 400, Orientation 6 → 400 × 600)
        assertThat(jdbc.queryForList("SELECT width, height FROM place_photos WHERE id = ?", photoIds.getFirst())
                        .getFirst())
                .containsEntry("width", 400L)
                .containsEntry("height", 600L);

        approve(placeId);
        mvc.perform(get(PLACES + "/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(2));
        mvc.perform(get(PLACES).param("city", city))
                .andExpect(jsonPath("$.items[0].coverPhoto.id").value(photoIds.getFirst()))
                .andExpect(jsonPath("$.items[0].coverPhoto.mediumUrl")
                        .value(PUBLIC_URL + PhotoSize.MEDIUM.keyOf(storageKey)));
    }

    @Test
    void oneInvalidPhotoRejectsTheWholeProposal() throws Exception {
        Account member = account("USER");
        String city = city();
        byte[] gif = "GIF89a....".getBytes(StandardCharsets.US_ASCII);

        perform(
                        member,
                        propose(placeJson("Quán Lỗi Ảnh", city))
                                .file(photo("ok.jpg", TestImages.jpeg(50, 50)))
                                .file(photo("gia-mao.jpg", gif)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"))
                .andExpect(jsonPath("$.errors[0].field").value("photos[1]"));

        assertThat(placesIn(city)).isZero();
    }

    @Test
    void atMostTenPhotosPerRequest() throws Exception {
        Account member = account("USER");
        MockMultipartHttpServletRequestBuilder request = propose(placeJson("Quán Nhiều Ảnh", city()));
        for (int i = 0; i < 11; i++) {
            request.file(photo("a" + i + ".jpg", TestImages.jpeg(20, 20)));
        }
        perform(member, request)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PHOTO_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.errors[0].field").value("photos"));
    }

    @Test
    void storageOutageIsA503AndCreatesNothing() throws Exception {
        Account member = account("USER");
        String city = city();
        storage.setFailing(true);
        try {
            perform(
                            member,
                            propose(placeJson("Quán Kho Sập", city))
                                    .file(photo("a.jpg", TestImages.jpeg(50, 50)))
                                    .file(photo("b.jpg", TestImages.jpeg(50, 50))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        } finally {
            storage.setFailing(false);
        }
        assertThat(placesIn(city)).isZero();
    }

    // ─── Chủ địa điểm thêm / xóa ảnh ─────────────────────────────────────────

    @Test
    void ownerAddsPhotosAndOthersCannot() throws Exception {
        Account owner = account("USER", "OWNER");
        Account stranger = account("USER", "OWNER");
        long placeId = ownedPlace(owner);

        perform(owner, addPhotos(placeId, photo("a.jpg", TestImages.jpeg(800, 600))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PROCESSING"));
        perform(stranger, addPhotos(placeId, photo("b.jpg", TestImages.jpeg(10, 10))))
                .andExpect(status().isForbidden());
        mvc.perform(addPhotos(placeId, photo("c.jpg", TestImages.jpeg(10, 10)))).andExpect(status().isUnauthorized());

        List<Long> ids = photoIdsOf(placeId);
        assertThat(ids).hasSize(1);
        awaitStatus(ids, "READY");
        assertThat(isCover(ids.getFirst())).isTrue();
    }

    @Test
    void placeHoldsAtMostThirtyPhotosNotCountingFailedOnes() throws Exception {
        Account owner = account("USER", "OWNER");
        long placeId = ownedPlace(owner);
        for (int i = 0; i < 28; i++) {
            insertPhoto(placeId, owner.id(), "READY", i == 0, i);
        }
        insertPhoto(placeId, owner.id(), "FAILED", false, 28); // ảnh lỗi không chiếm chỗ

        perform(
                        owner,
                        addPhotos(
                                placeId,
                                photo("a.jpg", TestImages.jpeg(10, 10)),
                                photo("b.jpg", TestImages.jpeg(10, 10)),
                                photo("c.jpg", TestImages.jpeg(10, 10))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PHOTO_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("30")));
        perform(
                        owner,
                        addPhotos(
                                placeId,
                                photo("a.jpg", TestImages.jpeg(10, 10)),
                                photo("b.jpg", TestImages.jpeg(10, 10))))
                .andExpect(status().isCreated());
        // Ảnh mới xếp sau ảnh cũ, không cướp bìa
        assertThat(jdbc.queryForObject(
                        "SELECT MAX(sort_order) FROM place_photos WHERE place_id = ?", Integer.class, placeId))
                .isEqualTo(30);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM place_photos WHERE place_id = ? AND is_cover", Integer.class, placeId))
                .isEqualTo(1);
    }

    @Test
    void deletingTheCoverHandsItToTheNextPhotoAndRemovesObjects() throws Exception {
        Account owner = account("USER", "OWNER");
        long placeId = ownedPlace(owner);
        perform(
                        owner,
                        addPhotos(
                                placeId,
                                photo("a.jpg", TestImages.jpeg(100, 100)),
                                photo("b.jpg", TestImages.jpeg(100, 100))))
                .andExpect(status().isCreated());
        List<Long> ids = photoIdsOf(placeId);
        awaitStatus(ids, "READY");
        long cover = ids.getFirst();
        String coverKey = storageKeyOf(cover);
        assertThat(isCover(cover)).isTrue();

        perform(owner, delete("/api/v1/owner/places/" + placeId + "/photos/" + cover))
                .andExpect(status().isNoContent());

        assertThat(photoIdsOf(placeId)).containsExactly(ids.get(1));
        assertThat(isCover(ids.get(1))).isTrue();
        assertThat(storage.keysStartingWith(coverKey)).isEmpty();

        // Ảnh không thuộc địa điểm này → 404, kể cả khi id tồn tại ở địa điểm khác
        long otherPlace = ownedPlace(owner);
        perform(owner, delete("/api/v1/owner/places/" + otherPlace + "/photos/" + ids.get(1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"));
    }

    // ─── Hiển thị & xử lý lỗi ────────────────────────────────────────────────

    @Test
    void visitorsSeeOnlyReadyPhotosWhileOwnerSeesProgress() throws Exception {
        Account owner = account("USER", "OWNER");
        long placeId = ownedPlace(owner);
        insertPhoto(placeId, owner.id(), "READY", true, 0);
        insertPhoto(placeId, owner.id(), "PROCESSING", false, 1);
        insertPhoto(placeId, owner.id(), "FAILED", false, 2);
        String slug = slugOf(placeId);

        mvc.perform(get(PLACES + "/" + slug))
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].status").value("READY"))
                .andExpect(jsonPath("$.coverPhoto.status").value("READY"));
        perform(owner, get(PLACES + "/" + slug))
                .andExpect(jsonPath("$.photos.length()").value(3))
                .andExpect(jsonPath("$.photos[1].status").value("PROCESSING"))
                .andExpect(jsonPath("$.photos[1].thumbUrl").isEmpty())
                .andExpect(jsonPath("$.photos[2].status").value("FAILED"));
    }

    @Test
    void undecodablePhotoIsMarkedFailedAndLosesTheCover() throws Exception {
        Account owner = account("USER", "OWNER");
        long placeId = ownedPlace(owner);
        long broken = insertPhoto(placeId, owner.id(), "PROCESSING", true, 0);
        long next = insertPhoto(placeId, owner.id(), "READY", false, 1);
        String incoming = "incoming/" + storageKeyOf(broken);
        // Qua được kiểm tra header lúc upload nhưng không giải mã được
        storage.put(incoming, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 9, 9, 9}, "image/jpeg");

        photoService.process(broken);

        assertThat(statusOf(broken)).isEqualTo("FAILED");
        assertThat(isCover(broken)).isFalse();
        assertThat(isCover(next)).isTrue();
        assertThat(storage.contains(incoming)).isFalse();

        // Message lặp lại (RabbitMQ giao lại) không làm gì thêm
        photoService.process(broken);
        assertThat(statusOf(broken)).isEqualTo("FAILED");
    }

    // ─── Tiện ích ────────────────────────────────────────────────────────────

    private record Account(long id, String email, String accessToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    private Account account(String... roles) throws Exception {
        String email = "photo-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử ảnh"}
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

    private static String city() {
        return "Thử Ảnh " + UUID.randomUUID().toString().substring(0, 8);
    }

    private String placeJson(String name, String city) {
        Long categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'pho-bun'", Long.class);
        return """
                {"name": "%s", "categoryId": %d, "address": "1 Phố Huế", "city": "%s",
                 "location": {"lat": 21.0175, "lng": 105.8523}}
                """.formatted(name, categoryId, city);
    }

    private static MockMultipartHttpServletRequestBuilder propose(String placeJson) {
        return multipart(PLACES)
                .file(new MockMultipartFile(
                        "place", "", MediaType.APPLICATION_JSON_VALUE, placeJson.getBytes(StandardCharsets.UTF_8)));
    }

    private static MockMultipartHttpServletRequestBuilder addPhotos(long placeId, MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/owner/places/" + placeId + "/photos");
        for (MockMultipartFile file : files) {
            request.file(file);
        }
        return request;
    }

    private static MockMultipartFile photo(String filename, byte[] content) {
        return new MockMultipartFile("photos", filename, "image/jpeg", content);
    }

    /** Địa điểm đã duyệt, {@code owner} là chủ. */
    private long ownedPlace(Account owner) throws Exception {
        String slug = read(
                perform(owner, propose(placeJson("Quán Của Chủ", city())))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "$.slug");
        long placeId = placeIdOf(slug);
        approve(placeId);
        jdbc.update("UPDATE places SET owner_id = ? WHERE id = ?", owner.id(), placeId);
        return placeId;
    }

    private void approve(long placeId) throws Exception {
        perform(
                        account("USER", "MODERATOR"),
                        post("/api/v1/moderation/places/" + placeId + "/decision")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\": \"APPROVE\"}"))
                .andExpect(status().isNoContent());
    }

    private long insertPhoto(long placeId, long userId, String status, boolean cover, int sortOrder) {
        String key = "places/" + placeId + "/" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO place_photos (place_id, uploaded_by, storage_key, status, is_cover, sort_order, created_at)
                VALUES (?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6))
                """, placeId, userId, key, status, cover, sortOrder);
        Long id = jdbc.queryForObject("SELECT id FROM place_photos WHERE storage_key = ?", Long.class, key);
        assertThat(id).isNotNull();
        return id;
    }

    /** Consumer chạy bất đồng bộ — chờ tới khi mọi ảnh đạt trạng thái mong đợi (tối đa 10 giây). */
    private void awaitStatus(List<Long> photoIds, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (photoIds.stream().allMatch(id -> expected.equals(statusOf(id)))) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Ảnh " + photoIds + " chưa đạt " + expected);
    }

    private List<Long> photoIdsOf(long placeId) {
        return jdbc.queryForList(
                "SELECT id FROM place_photos WHERE place_id = ? ORDER BY sort_order, id", Long.class, placeId);
    }

    private String statusOf(long photoId) {
        return jdbc.queryForObject("SELECT status FROM place_photos WHERE id = ?", String.class, photoId);
    }

    private boolean isCover(long photoId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT is_cover FROM place_photos WHERE id = ?", Boolean.class, photoId));
    }

    private String storageKeyOf(long photoId) {
        return jdbc.queryForObject("SELECT storage_key FROM place_photos WHERE id = ?", String.class, photoId);
    }

    private long placeIdOf(String slug) {
        Long id = jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    private String slugOf(long placeId) {
        return jdbc.queryForObject("SELECT slug FROM places WHERE id = ?", String.class, placeId);
    }

    private int placesIn(String city) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM places WHERE city = ?", Integer.class, city);
        return count == null ? 0 : count;
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
