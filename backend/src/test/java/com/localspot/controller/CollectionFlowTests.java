package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Bộ sưu tập (UC18, FR-26) qua HTTP trên MySQL thật: riêng tư chỉ chủ thấy (người khác 404 như không tồn tại), công khai
 * hiện trên trang cá nhân và qua link chia sẻ, thu hồi link khi về riêng tư, địa điểm bị ẩn lọc theo người xem, trần
 * 50 bộ / 100 địa điểm kể cả khi request chen nhau.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CollectionFlowTests {

    private static final String PASSWORD = "LocalSpot2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void privateCollectionIsOnlyVisibleToItsOwner() throws Exception {
        Account owner = account();
        Account other = account();

        long id = create(owner, "Quán ăn Hà Nội nên thử", false);

        perform(owner, get("/api/v1/collections/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPublic").value(false))
                .andExpect(jsonPath("$.shareSlug").isEmpty())
                .andExpect(jsonPath("$.owner.id").value(owner.id()))
                .andExpect(jsonPath("$.places.length()").value(0));
        perform(other, get("/api/v1/collections/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COLLECTION_NOT_FOUND"));
        mvc.perform(get("/api/v1/collections/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/" + owner.id() + "/collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        perform(owner, get("/api/v1/me/collections"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void publishingSharesAReadableLinkThatIsRevokedWhenPrivate() throws Exception {
        Account owner = account();
        long id = create(owner, "Quán ăn Hà Nội nên thử", false);

        String slug = shareSlugOf(update(owner, id, "Quán ăn Hà Nội nên thử", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPublic").value(true))
                .andReturn());
        assertThat(slug).matches("quan-an-ha-noi-nen-thu-[a-z0-9]{6}");

        mvc.perform(get("/api/v1/collections/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/shared/collections/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Quán ăn Hà Nội nên thử"));
        mvc.perform(get("/api/v1/users/" + owner.id() + "/collections"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].shareSlug").value(slug));
        // Sửa tên khi vẫn công khai: giữ link đã chia sẻ
        assertThat(shareSlugOf(update(owner, id, "Tên mới", true).andReturn())).isEqualTo(slug);

        update(owner, id, "Tên mới", false).andExpect(jsonPath("$.shareSlug").isEmpty());
        mvc.perform(get("/api/v1/shared/collections/" + slug)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/collections/" + id)).andExpect(status().isNotFound());

        String again = shareSlugOf(update(owner, id, "Tên mới", true).andReturn());
        assertThat(again).isNotEqualTo(slug).startsWith("ten-moi-");
        mvc.perform(get("/api/v1/shared/collections/" + slug)).andExpect(status().isNotFound()); // link cũ vẫn chết
    }

    @Test
    void placesAreAddedIdempotentlyNewestFirst() throws Exception {
        Account owner = account();
        long id = create(owner, "Cà phê làm việc", false);
        long first = place("APPROVED");
        long second = place("APPROVED");

        addPlace(owner, id, first, "{\"note\":\"Ghế êm\"}").andExpect(status().isNoContent());
        addPlace(owner, id, second, null).andExpect(status().isNoContent());
        addPlace(owner, id, first, "{\"note\":\"  Wifi mạnh  \"}").andExpect(status().isNoContent()); // chỉ đổi ghi chú
        addPlace(owner, id, second, null).andExpect(status().isNoContent()); // không body: giữ nguyên

        perform(owner, get("/api/v1/collections/" + id))
                .andExpect(jsonPath("$.placeCount").value(2))
                .andExpect(jsonPath("$.places[0].place.id").value(second))
                .andExpect(jsonPath("$.places[1].place.id").value(first))
                .andExpect(jsonPath("$.places[1].note").value("Wifi mạnh"))
                .andExpect(jsonPath("$.places[0].note").isEmpty());

        perform(owner, delete("/api/v1/collections/" + id + "/places/" + first)).andExpect(status().isNoContent());
        perform(owner, delete("/api/v1/collections/" + id + "/places/" + first)).andExpect(status().isNoContent());
        perform(owner, get("/api/v1/collections/" + id))
                .andExpect(jsonPath("$.placeCount").value(1));

        for (String status : List.of("PENDING", "REJECTED", "HIDDEN")) {
            addPlace(owner, id, place(status), null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
        }
        addPlace(owner, id, first, "{\"note\":\"" + "x".repeat(501) + "\"}")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void placesHiddenAfterAddingAreFilteredForEveryoneButTheOwner() throws Exception {
        Account owner = account();
        long id = create(owner, "Điểm check-in Đà Lạt", true);
        long visible = place("APPROVED");
        long laterHidden = place("APPROVED");
        addPlace(owner, id, visible, null).andExpect(status().isNoContent());
        addPlace(owner, id, laterHidden, null).andExpect(status().isNoContent());
        jdbc.update("UPDATE places SET status = 'HIDDEN' WHERE id = ?", laterHidden); // ẩn sau báo cáo vi phạm

        perform(owner, get("/api/v1/collections/" + id))
                .andExpect(jsonPath("$.placeCount").value(2))
                .andExpect(jsonPath("$.places[0].place.status").value("HIDDEN"));
        perform(owner, get("/api/v1/me/collections"))
                .andExpect(jsonPath("$[0].placeCount").value(2));
        mvc.perform(get("/api/v1/collections/" + id))
                .andExpect(jsonPath("$.placeCount").value(1))
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].place.id").value(visible));
        mvc.perform(get("/api/v1/users/" + owner.id() + "/collections"))
                .andExpect(jsonPath("$[0].placeCount").value(1));
        // Chủ dọn được địa điểm đã bị ẩn
        perform(owner, delete("/api/v1/collections/" + id + "/places/" + laterHidden))
                .andExpect(status().isNoContent());
        perform(owner, get("/api/v1/collections/" + id))
                .andExpect(jsonPath("$.placeCount").value(1));
    }

    @Test
    void coverIsTheNewestPlaceTheViewerCanSee() throws Exception {
        Account owner = account();
        long id = create(owner, "Có ảnh bìa", true);
        long older = place("APPROVED");
        long newest = place("APPROVED");
        readyCover(older, "places/" + older + "/older");
        readyCover(newest, "places/" + newest + "/newest");
        addPlace(owner, id, older, null).andExpect(status().isNoContent());
        Thread.sleep(5); // added_at khác nhau → thứ tự "thêm gần nhất" xác định
        addPlace(owner, id, newest, null).andExpect(status().isNoContent());

        perform(owner, get("/api/v1/me/collections"))
                .andExpect(jsonPath("$[0].coverPhoto.thumbUrl").value(containsString("newest")));
        jdbc.update("UPDATE places SET status = 'HIDDEN' WHERE id = ?", newest);
        // Người khác: bìa từ địa điểm mới nhất còn hiện; chủ vẫn thấy địa điểm mới nhất (kể cả đã bị ẩn)
        mvc.perform(get("/api/v1/users/" + owner.id() + "/collections"))
                .andExpect(jsonPath("$[0].coverPhoto.thumbUrl").value(containsString("older")));
        mvc.perform(get("/api/v1/collections/" + id))
                .andExpect(jsonPath("$.coverPhoto.thumbUrl").value(containsString("older")));
        perform(owner, get("/api/v1/me/collections"))
                .andExpect(jsonPath("$[0].coverPhoto.thumbUrl").value(containsString("newest")));
    }

    @Test
    void othersCannotTouchACollectionNorLearnThatItExists() throws Exception {
        Account owner = account();
        Account other = account();
        long id = create(owner, "Của tôi", true);
        long placeId = place("APPROVED");
        addPlace(owner, id, placeId, null).andExpect(status().isNoContent());

        update(other, id, "Chiếm", true).andExpect(status().isNotFound());
        addPlace(other, id, place("APPROVED"), null).andExpect(status().isNotFound());
        perform(other, delete("/api/v1/collections/" + id + "/places/" + placeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COLLECTION_NOT_FOUND"));
        perform(other, delete("/api/v1/collections/" + id)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Khách\"}"))
                .andExpect(status().isUnauthorized());

        perform(owner, get("/api/v1/collections/" + id))
                .andExpect(jsonPath("$.name").value("Của tôi"))
                .andExpect(jsonPath("$.placeCount").value(1));
    }

    @Test
    void deletingRemovesTheCollectionAndItsPlaces() throws Exception {
        Account owner = account();
        long id = create(owner, "Sẽ xóa", true);
        long placeId = place("APPROVED");
        addPlace(owner, id, placeId, null).andExpect(status().isNoContent());

        perform(owner, delete("/api/v1/collections/" + id)).andExpect(status().isNoContent());

        perform(owner, get("/api/v1/collections/" + id)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM collection_place WHERE collection_id = ?", Integer.class, id))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM places WHERE id = ?", Integer.class, placeId))
                .isEqualTo(1);
    }

    @Test
    void validation() throws Exception {
        Account owner = account();
        for (String body : List.of(
                "{\"name\":\"   \"}",
                "{}",
                "{\"name\":\"" + "x".repeat(151) + "\"}",
                "{\"name\":\"Ổn\",\"description\":\"" + "x".repeat(1001) + "\"}")) {
            perform(
                            owner,
                            post("/api/v1/collections")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        // Tên chỉ có ký tự không ra slug → tiền tố mặc định
        String slug = shareSlugOf(perform(
                        owner,
                        post("/api/v1/collections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"☕☕\",\"description\":\"  \",\"isPublic\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").isEmpty())
                .andReturn());
        assertThat(slug).matches("bo-suu-tap-[a-z0-9]{6}");
    }

    @Test
    void atMostFiftyCollectionsEvenUnderConcurrentRequests() throws Exception {
        Account owner = account();
        for (int i = 0; i < 48; i++) {
            jdbc.update("""
                    INSERT INTO collections (user_id, name, is_public, created_at, updated_at)
                    VALUES (?, 'Có sẵn', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                    """, owner.id());
        }
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> creates = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                creates.add(() -> perform(
                                owner,
                                post("/api/v1/collections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"Chen nhau\"}"))
                        .andReturn()
                        .getResponse()
                        .getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(creates)) {
                statuses.add(f.get());
            }
            assertThat(statuses.stream().filter(s -> s == 201)).hasSize(2);
            assertThat(statuses.stream().filter(s -> s == 422)).hasSize(4);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM collections WHERE user_id = ?", Integer.class, owner.id()))
                .isEqualTo(50);
        perform(
                        owner,
                        post("/api/v1/collections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Thứ 51\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COLLECTION_LIMIT_EXCEEDED"));
    }

    @Test
    void atMostOneHundredPlacesPerCollection() throws Exception {
        Account owner = account();
        long id = create(owner, "Đầy", false);
        for (int i = 0; i < 100; i++) {
            jdbc.update(
                    "INSERT INTO collection_place (collection_id, place_id, added_at) VALUES (?, ?, UTC_TIMESTAMP(6))",
                    id,
                    place("APPROVED"));
        }
        long extra = place("APPROVED");

        addPlace(owner, id, extra, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COLLECTION_LIMIT_EXCEEDED"));
        // Đã có trong bộ → vẫn sửa được ghi chú khi đủ 100
        Long existing = jdbc.queryForObject(
                "SELECT MIN(place_id) FROM collection_place WHERE collection_id = ?", Long.class, id);
        assertThat(existing).isNotNull();
        addPlace(owner, id, existing, "{\"note\":\"Vẫn sửa được\"}").andExpect(status().isNoContent());
    }

    // ─── Hỗ trợ ─────────────────────────────────────────────────────────────

    private record Account(long id, String accessToken) {}

    private Account account() throws Exception {
        String email = "collection-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử bộ sưu tập"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return new Account(id, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
    }

    private long place(String status) {
        Long proposer = jdbc.queryForObject("SELECT MIN(id) FROM users", Long.class);
        Long category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'ca-phe'", Long.class);
        String slug = "collection-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES (?, ?, 'Quán thử bộ sưu tập', ?, '1 Phố Thử', 'Hà Nội',
                        ST_PointFromText('POINT(21.03 105.85)', 4326), ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, category, proposer, slug, status);
        Long id = jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    private void readyCover(long placeId, String storageKey) {
        Long uploader = jdbc.queryForObject("SELECT MIN(id) FROM users", Long.class);
        jdbc.update("""
                INSERT INTO place_photos (place_id, uploaded_by, storage_key, status, is_cover, sort_order, width,
                                          height, created_at)
                VALUES (?, ?, ?, 'READY', TRUE, 0, 1200, 900, UTC_TIMESTAMP(6))
                """, placeId, uploader, storageKey);
    }

    private long create(Account owner, String name, boolean isPublic) throws Exception {
        MvcResult result = perform(
                        owner,
                        post("/api/v1/collections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"%s\",\"isPublic\":%s}".formatted(name, isPublic)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private ResultActions update(Account actor, long id, String name, boolean isPublic) throws Exception {
        return perform(
                actor,
                patch("/api/v1/collections/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"isPublic\":%s}".formatted(name, isPublic)));
    }

    private ResultActions addPlace(Account actor, long id, long placeId, String body) throws Exception {
        AbstractMockHttpServletRequestBuilder<?> request = put("/api/v1/collections/" + id + "/places/" + placeId);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return perform(actor, request);
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    private static String shareSlugOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.shareSlug");
    }
}
