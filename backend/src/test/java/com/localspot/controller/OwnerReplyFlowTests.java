package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Chủ địa điểm phản hồi review (UC25, FR-33) qua HTTP trên MySQL thật: tạo / sửa bằng một PUT, hiện dưới review công
 * khai, chỉ chủ hiện tại của địa điểm, chỉ review đang hiện, validate, xóa idempotent, chủ mới sửa phản hồi của chủ cũ,
 * hai lần bấm đồng thời.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OwnerReplyFlowTests {

    private static final String PASSWORD = "LocalSpot2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void ownerRepliesAndEditsShownUnderPublicReview() throws Exception {
        Account owner = account("OWNER");
        long placeId = place("APPROVED", owner);
        long reviewId = review(placeId, account(), "PUBLISHED");

        String created = perform(owner, reply(reviewId, "  Cảm ơn bạn đã ghé quán!  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Cảm ơn bạn đã ghé quán!"))
                .andExpect(jsonPath("$.owner.id").value(owner.id()))
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int replyId = JsonPath.read(created, "$.id");

        perform(owner, reply(reviewId, "Cảm ơn bạn, lần sau quán tặng trà đá nhé."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(replyId))
                .andExpect(jsonPath("$.content").value("Cảm ơn bạn, lần sau quán tặng trà đá nhé."));
        assertThat(repliesOn(reviewId)).isEqualTo(1);

        mvc.perform(get("/api/v1/places/" + placeId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].ownerReply.id").value(replyId))
                .andExpect(
                        jsonPath("$.items[0].ownerReply.content").value("Cảm ơn bạn, lần sau quán tặng trà đá nhé."));
    }

    @Test
    void unrepliedFilterListsOnlyReviewsWithoutOwnerReply() throws Exception {
        Account owner = account("OWNER");
        long placeId = place("APPROVED", owner);
        long answered = review(placeId, account(), "PUBLISHED");
        long waitingOld = review(placeId, account(), "PUBLISHED");
        long waitingNew = review(placeId, account(), "PUBLISHED");
        perform(owner, reply(answered, "Cảm ơn bạn đã ghé quán!")).andExpect(status().isOk());

        String path = "/api/v1/places/" + placeId + "/reviews";
        mvc.perform(get(path).param("unreplied", "true").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(waitingNew))
                .andExpect(jsonPath("$.nextCursor").isString());
        mvc.perform(get(path).param("unreplied", "true"))
                .andExpect(jsonPath("$.items[*].id", contains((int) waitingNew, (int) waitingOld)));
        mvc.perform(get(path).param("unreplied", "true").param("sort", "HELPFUL"))
                .andExpect(jsonPath("$.items.length()").value(2));
        // Mặc định không lọc
        mvc.perform(get(path)).andExpect(jsonPath("$.items.length()").value(3));
        // Xoá phản hồi → lại vào danh sách cần phản hồi
        perform(owner, delete(replyPath(answered))).andExpect(status().isNoContent());
        mvc.perform(get(path).param("unreplied", "true"))
                .andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void onlyTheCurrentOwnerOfThePlaceMayReply() throws Exception {
        Account owner = account("OWNER");
        Account author = account();
        long placeId = place("APPROVED", owner);
        long reviewId = review(placeId, author, "PUBLISHED");
        Account otherOwner = account("OWNER");
        place("APPROVED", otherOwner);

        for (Account actor : List.of(author, account(), otherOwner, account("MODERATOR"))) {
            perform(actor, reply(reviewId, "Không phải chủ")).andExpect(status().isForbidden());
            perform(actor, delete(replyPath(reviewId))).andExpect(status().isForbidden());
        }
        mvc.perform(reply(reviewId, "Khách")).andExpect(status().isUnauthorized());
        assertThat(repliesOn(reviewId)).isZero();
    }

    @Test
    void onlyVisibleReviewsCanBeRepliedTo() throws Exception {
        Account owner = account("OWNER");
        long placeId = place("APPROVED", owner);
        for (String status : List.of("PENDING", "REJECTED", "HIDDEN")) {
            perform(owner, reply(review(placeId, account(), status), "Phản hồi"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
        }
        long hiddenPlace = place("HIDDEN", owner);
        perform(owner, reply(review(hiddenPlace, account(), "PUBLISHED"), "Phản hồi"))
                .andExpect(status().isNotFound());
        long deleted = review(placeId, account(), "PUBLISHED");
        jdbc.update("UPDATE reviews SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);
        perform(owner, reply(deleted, "Phản hồi")).andExpect(status().isNotFound());
        perform(owner, reply(999_999_999L, "Phản hồi"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    }

    @Test
    void replyContentIsValidated() throws Exception {
        Account owner = account("OWNER");
        long reviewId = review(place("APPROVED", owner), account(), "PUBLISHED");

        for (String content : List.of("", "   ", "a".repeat(2001))) {
            perform(owner, reply(reviewId, content))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("content"));
        }
        perform(owner, reply(reviewId, "a".repeat(2000))).andExpect(status().isOk());
    }

    @Test
    void deleteIsIdempotentAndWorksAfterReviewIsHidden() throws Exception {
        Account owner = account("OWNER");
        long placeId = place("APPROVED", owner);
        long reviewId = review(placeId, account(), "PUBLISHED");
        perform(owner, reply(reviewId, "Cảm ơn bạn!")).andExpect(status().isOk());

        // Review bị ẩn sau báo cáo — chủ vẫn gỡ được phản hồi của mình
        jdbc.update("UPDATE reviews SET status = 'HIDDEN' WHERE id = ?", reviewId);
        perform(owner, delete(replyPath(reviewId))).andExpect(status().isNoContent());
        assertThat(repliesOn(reviewId)).isZero();
        perform(owner, delete(replyPath(reviewId))).andExpect(status().isNoContent());
    }

    @Test
    void newOwnerTakesOverPreviousOwnersReply() throws Exception {
        Account previous = account("OWNER");
        long placeId = place("APPROVED", previous);
        long reviewId = review(placeId, account(), "PUBLISHED");
        perform(previous, reply(reviewId, "Chủ cũ trả lời")).andExpect(status().isOk());

        Account current = account("OWNER");
        jdbc.update("UPDATE places SET owner_id = ? WHERE id = ?", current.id(), placeId);
        perform(previous, reply(reviewId, "Chủ cũ sửa")).andExpect(status().isForbidden());
        perform(current, reply(reviewId, "Chủ mới trả lời"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner.id").value(current.id()));
        assertThat(jdbc.queryForObject("SELECT user_id FROM owner_replies WHERE review_id = ?", Long.class, reviewId))
                .isEqualTo(current.id());
    }

    @Test
    void concurrentFirstRepliesCreateOneRow() throws Exception {
        Account owner = account("OWNER");
        long reviewId = review(place("APPROVED", owner), account(), "PUBLISHED");
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<MvcResult>> clicks = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                String content = "Cảm ơn bạn lần " + i;
                clicks.add(() -> perform(owner, reply(reviewId, content)).andReturn());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<MvcResult> f : pool.invokeAll(clicks)) {
                statuses.add(f.get().getResponse().getStatus());
            }
            assertThat(statuses).containsOnly(200);
        } finally {
            pool.shutdownNow();
        }
        assertThat(repliesOn(reviewId)).isEqualTo(1);
    }

    // ─── Dữ liệu thử ────────────────────────────────────────────────────────

    private record Account(long id, String accessToken) {}

    private Account account(String... roles) throws Exception {
        String email = "reply-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử phản hồi"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
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
        return new Account(id, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
    }

    private long place(String status, Account owner) throws Exception {
        Account proposer = account();
        Long category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'pho-bun'", Long.class);
        String slug = "reply-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, owner_id, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES (?, ?, ?, 'Quán thử phản hồi', ?, '1 Phố Thử', 'Hà Nội',
                        ST_PointFromText('POINT(21.03 105.85)', 4326), ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, category, proposer.id(), owner.id(), slug, status);
        Long id = jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
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

    private static String replyPath(long reviewId) {
        return "/api/v1/reviews/" + reviewId + "/reply";
    }

    private static AbstractMockHttpServletRequestBuilder<?> reply(long reviewId, String content) {
        return put(replyPath(reviewId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"" + content + "\"}");
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    private int repliesOn(long reviewId) {
        Integer count =
                jdbc.queryForObject("SELECT COUNT(*) FROM owner_replies WHERE review_id = ?", Integer.class, reviewId);
        return count == null ? 0 : count;
    }
}
