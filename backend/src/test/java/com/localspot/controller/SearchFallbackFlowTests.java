package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.localspot.search.PlaceSearchIndex;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;

/**
 * Fallback khi Meilisearch sập (U9, UC08 ngoại lệ 3b): dừng hẳn container Meilisearch của context này → {@code GET
 * /search} vẫn trả kết quả từ MySQL {@code LIKE} trên tên, {@code degraded = true}, và không gọi lại Meilisearch trong
 * thời gian chờ sau lỗi.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchFallbackFlowTests {

    private static final String PASSWORD = "LocalSpot2026";
    private static final String SEARCH = "/api/v1/search";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier("meilisearchContainer")
    private GenericContainer<?> meilisearch;

    @MockitoSpyBean
    private PlaceSearchIndex index;

    @BeforeEach
    void stopMeilisearch() {
        if (meilisearch.isRunning()) {
            meilisearch.stop();
        }
    }

    @Test
    void searchesNamesInMySqlWhenMeilisearchIsDown() throws Exception {
        String token = token();
        long category = category();
        long phoThin = place(token, "Phở Thìn Lò Đúc", category, "4.200", 30000);
        long batDan = place(token, "Phở Bát Đàn", category, "4.600", 50000);
        long traSua = place(token, "Trà sữa Đông Đô", category, "3.900", 25000);
        long kem100 = place(token, "Kem 100% sữa", category, "3.000", null);
        place(token, "Kem 1000 vị", category, "3.100", null);

        // Không phân biệt dấu (collation utf8mb4_0900_ai_ci), mọi từ phải có trong tên, không cần đúng thứ tự
        assertThat(searchIds("pho thin", category)).containsExactly(phoThin);
        assertThat(searchIds("thìn phở", category)).containsExactly(phoThin);
        // Từ chung chung bị bỏ như khi có Meilisearch; RELEVANCE → xếp theo điểm Bayesian
        assertThat(searchIds("quán phở", category)).containsExactly(batDan, phoThin);
        // Collation coi cả đ = d (đã kiểm) — fallback vẫn tìm được khi gõ không dấu
        assertThat(searchIds("đông đô", category)).containsExactly(traSua);
        assertThat(searchIds("dong do", category)).containsExactly(traSua);
        // Ký tự đại diện của LIKE trong từ khóa được hiểu theo nghĩa đen
        assertThat(searchIds("100%", category)).containsExactly(kem100);
        // Bộ lọc, thứ tự
        assertThat(searchIds("pho", category, "priceMax", "40000")).containsExactly(phoThin);
        assertThat(searchIds("pho", category, "sort", "NEWEST")).containsExactly(batDan, phoThin);

        // Phân trang theo vị trí, cùng định dạng cursor
        List<Long> paged = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletRequestBuilder request = searchRequest("pho", category, "limit", "1");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            String body = mvc.perform(request).andReturn().getResponse().getContentAsString();
            paged.addAll(ids(body));
            cursor = JsonPath.read(body, "$.nextCursor");
        } while (cursor != null);
        assertThat(paged).containsExactly(batDan, phoThin);
    }

    @Test
    void doesNotRetryMeilisearchOnEveryRequestAfterAFailure() throws Exception {
        long category = category();
        clearInvocations(index);

        searchIds("pho", category);
        searchIds("bun cha", category);
        searchIds("cafe", category);

        // Lần lỗi đầu (nếu test khác chưa gây lỗi) bật thời gian chờ 30 s — các request sau đi thẳng fallback
        verify(index, atMost(1)).search(any());
    }

    // ─── Tiện ích ────────────────────────────────────────────────────────────

    private List<Long> searchIds(String q, long categoryId, String... params) throws Exception {
        String body = mvc.perform(searchRequest(q, categoryId, params))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(true))
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

    private static List<Long> ids(String body) {
        List<Number> ids = JsonPath.read(body, "$.items[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private long category() {
        String slug = "du-phong-" + UUID.randomUUID();
        jdbc.update("INSERT INTO categories (name, slug, sort_order) VALUES ('Danh mục dự phòng', ?, 0)", slug);
        Long id = jdbc.queryForObject("SELECT id FROM categories WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    /** Đề xuất qua API rồi đặt APPROVED + điểm trực tiếp (cột dẫn xuất — chỉ luồng tính rating được ghi). */
    private long place(String token, String name, long categoryId, String score, Integer priceMin) throws Exception {
        String json = """
                {"name": "%s", "categoryId": %d, "address": "1 Phố Thử", "city": "Hà Nội",
                 "location": {"lat": 21.03, "lng": 105.85}%s}
                """.formatted(name, categoryId, priceMin == null ? "" : ", \"priceMin\": " + priceMin);
        MvcResult result = mvc.perform(multipart("/api/v1/places")
                        .file(new MockMultipartFile(
                                "place", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        jdbc.update(
                "UPDATE places SET status = 'APPROVED', bayesian_score = ? WHERE id = ?", new BigDecimal(score), id);
        return id;
    }

    private String token() throws Exception {
        String email = "fallback-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử dự phòng"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }
}
