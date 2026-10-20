package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Bình luận review (UC15, FR-21) qua HTTP trên MySQL thật: 2 cấp (Q5), thứ tự hội thoại, gốc đã xóa còn trả lời thành
 * placeholder (chốt 2026-10-11), cần email đã xác thực, chỉ trên review đang hiện, xóa mềm của mình.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommentFlowTests {

    private static final String PASSWORD = "LocalSpot2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void rootsAndRepliesReadLikeAConversation() throws Exception {
        Account alice = account(true);
        Account bob = account(true);
        long placeId = approvedPlace();
        long reviewId = review(placeId, account(true), "PUBLISHED");

        long first = idOf(comment(alice, reviewId, "  Quán này có chỗ gửi xe máy không?  ", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Quán này có chỗ gửi xe máy không?"))
                .andExpect(jsonPath("$.author.id").value(alice.id()))
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.parentId").isEmpty())
                .andExpect(jsonPath("$.deleted").value(false))
                .andExpect(jsonPath("$.replies").isEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn());
        long second =
                idOf(comment(bob, reviewId, "Mình thấy phở hơi mặn.", null).andReturn());
        long reply1 = idOf(comment(bob, reviewId, "Có, gửi ở ngõ bên cạnh.", first)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(first))
                .andExpect(jsonPath("$.replies").doesNotExist())
                .andReturn());
        long reply2 = idOf(comment(alice, reviewId, "Cảm ơn bạn!", first).andReturn());
        long reply3 = idOf(comment(alice, reviewId, "Mình ăn thấy vừa.", second).andReturn());

        // Khách đọc được; gốc cũ trước, trả lời cũ trước
        mvc.perform(get(commentsOf(reviewId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.items[0].replies[*].id").value(contains((int) reply1, (int) reply2)))
                .andExpect(jsonPath("$.items[0].replies[0].author.id").value(bob.id()))
                .andExpect(jsonPath("$.items[0].replies[0].replies").isEmpty())
                .andExpect(jsonPath("$.items[1].id").value(second))
                .andExpect(jsonPath("$.items[1].replies[0].id").value(reply3))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        // Số bình luận trên thẻ review tính cả trả lời
        mvc.perform(get("/api/v1/places/" + placeId + "/reviews"))
                .andExpect(jsonPath("$.items[0].commentCount").value(5));
    }

    @Test
    void onlyTwoLevelsAndValidInput() throws Exception {
        Account member = account(true);
        long reviewId = review(approvedPlace(), account(true), "PUBLISHED");
        long otherReviewId = review(approvedPlace(), account(true), "PUBLISHED");
        long root = idOf(comment(member, reviewId, "Bình luận gốc.", null).andReturn());
        long reply = idOf(comment(member, reviewId, "Trả lời.", root).andReturn());
        long rootElsewhere =
                idOf(comment(member, otherReviewId, "Gốc ở review khác.", null).andReturn());

        comment(member, reviewId, "Trả lời một trả lời.", reply)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMMENT_DEPTH_EXCEEDED"))
                .andExpect(jsonPath("$.errors[0].field").value("parentId"));
        comment(member, reviewId, "Trả lời gốc của review khác.", rootElsewhere)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("parentId"));
        comment(member, reviewId, "Trả lời bình luận không có.", 999_999_999L)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("parentId"));
        comment(member, reviewId, "    ", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("content"));
        comment(member, reviewId, "a".repeat(2001), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("content"));
        comment(member, reviewId, "a".repeat(2000), null).andExpect(status().isCreated());
    }

    @Test
    void needsVerifiedEmailAndAVisibleReview() throws Exception {
        Account verified = account(true);
        long reviewId = review(approvedPlace(), account(true), "PUBLISHED");

        comment(account(false), reviewId, "Chưa xác thực email.", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        mvc.perform(post(commentsOf(reviewId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Khách\"}"))
                .andExpect(status().isUnauthorized());

        for (String status : List.of("PENDING", "REJECTED", "HIDDEN")) {
            long invisible = review(approvedPlace(), account(true), status);
            comment(verified, invisible, "Bình luận review chưa hiện.", null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
            mvc.perform(get(commentsOf(invisible))).andExpect(status().isNotFound());
        }
        long deleted = review(approvedPlace(), account(true), "PUBLISHED");
        jdbc.update("UPDATE reviews SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);
        mvc.perform(get(commentsOf(deleted))).andExpect(status().isNotFound());
    }

    @Test
    void deletingARootWithRepliesLeavesAPlaceholder() throws Exception {
        Account alice = account(true);
        Account bob = account(true);
        long placeId = approvedPlace();
        long reviewId = review(placeId, account(true), "PUBLISHED");
        long root = idOf(comment(alice, reviewId, "Gốc sẽ bị xóa.", null).andReturn());
        long reply = idOf(comment(bob, reviewId, "Trả lời của Bob.", root).andReturn());
        long lonely =
                idOf(comment(alice, reviewId, "Gốc không ai trả lời.", null).andReturn());

        perform(bob, delete("/api/v1/comments/" + root)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/comments/" + root)).andExpect(status().isUnauthorized());
        perform(alice, delete("/api/v1/comments/" + root)).andExpect(status().isNoContent());
        perform(alice, delete("/api/v1/comments/" + root))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
        perform(alice, delete("/api/v1/comments/" + lonely)).andExpect(status().isNoContent());

        // Gốc đã xóa còn trả lời → placeholder; gốc không có trả lời → biến mất
        mvc.perform(get(commentsOf(reviewId)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(root))
                .andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].author").isEmpty())
                .andExpect(jsonPath("$.items[0].content").isEmpty())
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].replies[0].id").value(reply))
                .andExpect(jsonPath("$.items[0].replies[0].content").value("Trả lời của Bob."));
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM comments WHERE id = ?", Boolean.class, root))
                .isTrue(); // xóa mềm
        mvc.perform(get("/api/v1/places/" + placeId + "/reviews"))
                .andExpect(jsonPath("$.items[0].commentCount").value(1)); // placeholder không tính

        comment(bob, reviewId, "Trả lời gốc đã xóa.", root)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("parentId"));

        // Trả lời cuối cùng bị xóa → gốc biến mất hẳn
        perform(bob, delete("/api/v1/comments/" + reply)).andExpect(status().isNoContent());
        mvc.perform(get(commentsOf(reviewId))).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void hiddenRootBehavesLikeADeletedOne() throws Exception {
        Account member = account(true);
        long reviewId = review(approvedPlace(), account(true), "PUBLISHED");
        long root = idOf(comment(member, reviewId, "Gốc bị báo cáo.", null).andReturn());
        long reply = idOf(comment(member, reviewId, "Trả lời vẫn hiện.", root).andReturn());
        long hiddenReply =
                idOf(comment(member, reviewId, "Trả lời bị ẩn.", root).andReturn());
        jdbc.update("UPDATE comments SET status = 'HIDDEN' WHERE id IN (?, ?)", root, hiddenReply);

        mvc.perform(get(commentsOf(reviewId)))
                .andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].content").isEmpty())
                .andExpect(jsonPath("$.items[0].replies.length()").value(1))
                .andExpect(jsonPath("$.items[0].replies[0].id").value(reply));
    }

    @Test
    void twentyFirstCommentWithinTenMinutesIs429ButInvalidOnesDoNotCount() throws Exception {
        Account member = account(true);
        long reviewId = review(approvedPlace(), account(true), "PUBLISHED");

        // Gõ sai không tiêu suất (chỉ tính lần đã qua mọi kiểm tra)
        comment(member, reviewId, "   ", null).andExpect(status().isUnprocessableContent());
        for (int i = 1; i <= 20; i++) {
            comment(member, reviewId, "Bình luận số " + i + ".", null).andExpect(status().isCreated());
        }
        comment(member, reviewId, "Bình luận thứ 21.", null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        // Giới hạn theo tài khoản — người khác vẫn bình luận được
        comment(account(true), reviewId, "Tài khoản khác không bị ảnh hưởng.", null)
                .andExpect(status().isCreated());
    }

    @Test
    void rootsArePagedOldestFirst() throws Exception {
        Account member = account(true);
        long reviewId = review(approvedPlace(), account(true), "PUBLISHED");
        long r1 = idOf(comment(member, reviewId, "Một.", null).andReturn());
        long r2 = idOf(comment(member, reviewId, "Hai.", null).andReturn());
        long r3 = idOf(comment(member, reviewId, "Ba.", null).andReturn());
        comment(member, reviewId, "Trả lời không chiếm chỗ trong trang.", r1);

        MvcResult page1 = mvc.perform(get(commentsOf(reviewId) + "?limit=2"))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) r1, (int) r2)))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn();
        String cursor = JsonPath.read(page1.getResponse().getContentAsString(), "$.nextCursor");
        mvc.perform(get(commentsOf(reviewId) + "?limit=2&cursor=" + cursor))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) r3)))
                .andExpect(jsonPath("$.nextCursor").isEmpty());

        mvc.perform(get(commentsOf(reviewId) + "?cursor=khong-hop-le"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mvc.perform(get(commentsOf(reviewId) + "?limit=51")).andExpect(status().isUnprocessableContent());
    }

    // ─── Hỗ trợ ─────────────────────────────────────────────────────────────

    private record Account(long id, String accessToken) {}

    private Account account(boolean emailVerified) throws Exception {
        String email = "comment-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử bình luận"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        if (emailVerified) {
            jdbc.update("UPDATE users SET email_verified_at = UTC_TIMESTAMP(6) WHERE id = ?", id);
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

    private long approvedPlace() {
        Long proposer = jdbc.queryForObject("SELECT MIN(id) FROM users", Long.class);
        Long category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'pho-bun'", Long.class);
        String slug = "comment-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES (?, ?, 'Quán thử bình luận', ?, '1 Phố Thử', 'Hà Nội',
                        ST_PointFromText('POINT(21.03 105.85)', 4326), 'APPROVED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, category, proposer, slug);
        Long id = jdbc.queryForObject("SELECT id FROM places WHERE slug = ?", Long.class, slug);
        assertThat(id).isNotNull();
        return id;
    }

    /** Review chèn thẳng — không đi qua cổng chống review ảo (đã có test riêng), chỉ cần trạng thái mong muốn. */
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

    private ResultActions comment(Account author, long reviewId, String content, Long parentId) throws Exception {
        String json = """
                {"content": "%s", "parentId": %s}
                """.formatted(content, parentId);
        return perform(
                author,
                post(commentsOf(reviewId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    private static String commentsOf(long reviewId) {
        return "/api/v1/reviews/" + reviewId + "/comments";
    }

    private static long idOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
