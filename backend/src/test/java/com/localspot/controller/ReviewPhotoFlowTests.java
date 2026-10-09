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
import com.localspot.event.ReviewChangedEvent;
import com.localspot.service.PhotoSize;
import com.localspot.service.TestImages;
import com.localspot.storage.InMemoryObjectStorage;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Checklist E7 qua HTTP + RabbitMQ thật (consumer bật riêng cho lớp này): ảnh kèm review khi viết và thêm / xóa sau
 * (chốt 2026-10-09) — cùng pipeline với ảnh địa điểm, giới hạn 10 ảnh mỗi review, ảnh chưa READY chỉ tác giả thấy, thêm
 * ảnh không đổi trạng thái review. Kho object là {@link InMemoryObjectStorage}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class ReviewPhotoFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String PUBLIC_URL = "http://cdn.test/localspot/";
    private static final String CONTENT = "Không gian thoáng, đồ uống ngon, giá hợp lý.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InMemoryObjectStorage storage;

    @Autowired
    private ApplicationEvents events;

    /** Consumer mail cũng bật theo — mail xác thực của tài khoản thử không gửi ra SMTP thật. */
    @MockitoBean
    @SuppressWarnings("unused")
    private JavaMailSender mailSender;

    @Test
    void photosSentWithTheReviewAreProcessedAndShownOnTheReview() throws Exception {
        Account author = account();
        long placeId = approvedPlace();

        MvcResult created = perform(
                        author,
                        createRequest(placeId)
                                .file(photo("a.jpg", TestImages.rotatedWithGps()))
                                .file(photo("b.jpg", TestImages.jpeg(800, 600))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andExpect(jsonPath("$.photos[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$.photos[0].thumbUrl").isEmpty())
                .andReturn();
        long reviewId = idOf(created);
        List<Long> photoIds = photoIdsOf(reviewId);
        awaitStatus(photoIds, "READY");

        String storageKey = storageKeyOf(photoIds.getFirst());
        assertThat(storageKey).startsWith("reviews/" + reviewId + "/");
        mvc.perform(get("/api/v1/places/" + placeId + "/reviews"))
                .andExpect(jsonPath("$.items[0].photos.length()").value(2))
                .andExpect(jsonPath("$.items[0].photos[0].id").value(photoIds.getFirst()))
                .andExpect(jsonPath("$.items[0].photos[0].thumbUrl")
                        .value(PUBLIC_URL + PhotoSize.THUMB.keyOf(storageKey)));
        // Ảnh gốc (còn GPS) đã bị xóa khỏi kho; các bản đã có
        assertThat(storage.keysStartingWith("incoming/reviews/" + reviewId + "/"))
                .isEmpty();
        assertThat(storage.contains(PhotoSize.LARGE.keyOf(storageKey))).isTrue();
        // Không gộp vào gallery của địa điểm (chốt 2026-10-09)
        String slug = jdbc.queryForObject("SELECT slug FROM places WHERE id = ?", String.class, placeId);
        mvc.perform(get("/api/v1/places/" + slug))
                .andExpect(jsonPath("$.photos").isEmpty());
    }

    @Test
    void atMostTenPhotosPerReviewAndBadFilesRejectTheWholeRequest() throws Exception {
        Account author = account();
        long placeId = approvedPlace();

        // Ảnh hỏng → cả request bị từ chối, review không được tạo
        perform(
                        author,
                        createRequest(placeId)
                                .file(photo("ok.jpg", TestImages.jpeg(100, 100)))
                                .file(photo("fake.jpg", "không phải ảnh".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("photos[1]"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE user_id = ?", Long.class, author.id()))
                .isZero();
        assertThat(storage.keysStartingWith("incoming/reviews/")).allMatch(key -> !key.contains("/fake"));

        long reviewId = idOf(perform(
                        author,
                        createRequest(placeId)
                                .file(photo("1.jpg", TestImages.jpeg(64, 64)))
                                .file(photo("2.jpg", TestImages.jpeg(64, 64))))
                .andExpect(status().isCreated())
                .andReturn());

        // 2 + 9 > 10 → 422; 2 + 8 = 10 → được
        perform(author, addRequest(reviewId, 9))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PHOTO_LIMIT_EXCEEDED"));
        perform(author, addRequest(reviewId, 8))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].status").value("PROCESSING"));
        perform(author, addRequest(reviewId, 1)).andExpect(status().isUnprocessableContent());
        assertThat(photoIdsOf(reviewId)).hasSize(10);
    }

    @Test
    void authorAddsAndRemovesPhotosWithoutChangingTheReviewStatus() throws Exception {
        Account author = account();
        long placeId = approvedPlace();
        long reviewId = idOf(perform(author, createRequest(placeId))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.photos").isEmpty())
                .andReturn());
        events.clear();

        perform(author, addRequest(reviewId, 2)).andExpect(status().isCreated());
        List<Long> photoIds = photoIdsOf(reviewId);
        awaitStatus(photoIds, "READY");
        // Ảnh không duyệt lại: review vẫn PUBLISHED, không tính lại rating
        assertThat(jdbc.queryForObject("SELECT status FROM reviews WHERE id = ?", String.class, reviewId))
                .isEqualTo("PUBLISHED");
        assertThat(events.stream(ReviewChangedEvent.class)).isEmpty();

        String removedKey = storageKeyOf(photoIds.getFirst());
        perform(author, delete("/api/v1/reviews/" + reviewId + "/photos/" + photoIds.getFirst()))
                .andExpect(status().isNoContent());
        assertThat(photoIdsOf(reviewId)).containsExactly(photoIds.get(1));
        assertThat(storage.contains(PhotoSize.LARGE.keyOf(removedKey))).isFalse(); // object xóa sau commit

        // Ảnh của review khác → 404; người khác → 403
        long otherReview = idOf(perform(account(), createRequest(placeId)).andReturn());
        perform(author, delete("/api/v1/reviews/" + reviewId + "/photos/" + otherPhotoOf(otherReview)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"));
        Account stranger = account();
        perform(stranger, addRequest(reviewId, 1)).andExpect(status().isForbidden());
        perform(stranger, delete("/api/v1/reviews/" + reviewId + "/photos/" + photoIds.get(1)))
                .andExpect(status().isForbidden());

        // Review bị ẩn sau báo cáo → khóa cả ảnh
        jdbc.update("UPDATE reviews SET status = 'HIDDEN' WHERE id = ?", reviewId);
        perform(author, addRequest(reviewId, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_LOCKED"));
        perform(author, delete("/api/v1/reviews/" + reviewId + "/photos/" + photoIds.get(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_LOCKED"));
    }

    @Test
    void unreadyPhotosAreOnlyShownToTheAuthorAndFailedOnesDoNotCountTowardsTheLimit() throws Exception {
        Account author = account();
        long placeId = approvedPlace();
        // Header PNG hợp lệ (qua được kiểm tra upload) nhưng không giải mã được → FAILED khi xử lý
        long reviewId = idOf(perform(
                        author,
                        createRequest(placeId)
                                .file(photo("good.jpg", TestImages.jpeg(64, 64)))
                                .file(new MockMultipartFile(
                                        "photos", "broken.png", "image/png", TestImages.pngHeaderOnly(64, 64))))
                .andExpect(status().isCreated())
                .andReturn());
        List<Long> photoIds = photoIdsOf(reviewId);
        awaitStatus(List.of(photoIds.getFirst()), "READY");
        awaitStatus(List.of(photoIds.get(1)), "FAILED");

        mvc.perform(get("/api/v1/places/" + placeId + "/reviews"))
                .andExpect(jsonPath("$.items[0].photos.length()").value(1))
                .andExpect(jsonPath("$.items[0].photos[0].status").value("READY"));
        perform(author, get("/api/v1/me/reviews"))
                .andExpect(jsonPath("$.items[0].photos.length()").value(2))
                .andExpect(jsonPath("$.items[0].photos[1].status").value("FAILED"));

        // 1 ảnh tính vào giới hạn (ảnh lỗi không tính) → thêm được 9
        perform(author, addRequest(reviewId, 9)).andExpect(status().isCreated());
    }

    // ─── Tiện ích ────────────────────────────────────────────────────────────

    private MockMultipartHttpServletRequestBuilder createRequest(long placeId) {
        String json = """
                {"rating": 5, "content": "%s", "visitedAt": "%s"}
                """.formatted(CONTENT, LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        return multipart("/api/v1/places/" + placeId + "/reviews")
                .file(new MockMultipartFile(
                        "review", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8)));
    }

    private static MockMultipartHttpServletRequestBuilder addRequest(long reviewId, int count) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/reviews/" + reviewId + "/photos");
        for (int i = 0; i < count; i++) {
            request.file(photo(i + ".jpg", TestImages.jpeg(48, 48)));
        }
        return request;
    }

    private static MockMultipartFile photo(String filename, byte[] content) {
        return new MockMultipartFile("photos", filename, "image/jpeg", content);
    }

    private long otherPhotoOf(long reviewId) {
        jdbc.update("""
                INSERT INTO review_photos (review_id, uploaded_by, storage_key, status, sort_order, created_at)
                SELECT id, user_id, CONCAT('reviews/', id, '/x'), 'READY', 0, UTC_TIMESTAMP(6) FROM reviews WHERE id = ?
                """, reviewId);
        return photoIdsOf(reviewId).getFirst();
    }

    private List<Long> photoIdsOf(long reviewId) {
        return jdbc.queryForList(
                "SELECT id FROM review_photos WHERE review_id = ? ORDER BY sort_order, id", Long.class, reviewId);
    }

    private String storageKeyOf(long photoId) {
        return jdbc.queryForObject("SELECT storage_key FROM review_photos WHERE id = ?", String.class, photoId);
    }

    /** Consumer chạy bất đồng bộ — chờ tới khi mọi ảnh đạt trạng thái mong đợi (tối đa 10 giây). */
    private void awaitStatus(List<Long> photoIds, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (photoIds.stream()
                    .allMatch(id -> expected.equals(
                            jdbc.queryForObject("SELECT status FROM review_photos WHERE id = ?", String.class, id)))) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Ảnh " + photoIds + " chưa đạt " + expected);
    }

    private record Account(long id, String accessToken) {}

    /** Thành viên tin cậy (trust = 30): review đăng ngay. */
    private Account account() throws Exception {
        String email = "review-photo-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử ảnh"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        jdbc.update(
                "UPDATE users SET email_verified_at = UTC_TIMESTAMP(6), created_at = UTC_TIMESTAMP(6) - INTERVAL 200 DAY,"
                        + " helpful_votes_count = 1 WHERE id = ?",
                id);
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new Account(id, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
    }

    private long approvedPlace() {
        Long proposer = jdbc.queryForObject("SELECT MIN(id) FROM users", Long.class);
        Long category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'ca-phe'", Long.class);
        String slug = "review-photo-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES (?, ?, 'Quán thử ảnh', ?, '1 Phố Thử', 'Hà Nội',
                        ST_PointFromText('POINT(21.03 105.85)', 4326), 'APPROVED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, category, proposer, slug);
        Long id = jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    private static long idOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
