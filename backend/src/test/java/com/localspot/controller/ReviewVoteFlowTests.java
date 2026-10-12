package com.localspot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.localspot.TestcontainersConfiguration;
import com.localspot.entity.Review;
import com.localspot.entity.User;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Vote "hữu ích" (UC14, FR-20) qua HTTP trên MySQL thật: idempotent, hai bộ đếm ({@code reviews.helpful_count} mọi vote,
 * {@code users.helpful_votes_count} chỉ vote {@code counted} — requirements §5.1), không vote review của mình, chỉ vote
 * review đang hiện, an toàn khi request chen nhau và khi review / người dùng được sửa qua entity cùng lúc.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewVoteFlowTests {

    private static final String PASSWORD = "LocalSpot2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void voteIsIdempotentAndOnlyTrustedVotesRaiseTheAuthorsTrust() throws Exception {
        Account author = account(Trust.NEW_VERIFIED);
        Account trusted = account(Trust.TRUSTED);
        Account newcomer = account(Trust.NEW_VERIFIED); // trust 10 < 30 → vote không tính vào trust
        long placeId = approvedPlace();
        long reviewId = review(placeId, author, "PUBLISHED");

        vote(trusted, reviewId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulCount").value(1))
                .andExpect(jsonPath("$.votedByMe").value(true));
        vote(trusted, reviewId) // PUT lần hai: không đổi gì
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulCount").value(1));
        assertThat(helpfulVotesCount(author)).isEqualTo(1);
        assertThat(counted(reviewId, trusted)).isTrue();

        vote(newcomer, reviewId).andExpect(jsonPath("$.helpfulCount").value(2));
        assertThat(helpfulVotesCount(author)).isEqualTo(1); // vote của người trust thấp chỉ để hiển thị
        assertThat(counted(reviewId, newcomer)).isFalse();

        // Danh sách: votedByMe theo người xem, helpfulCount chung
        perform(trusted, get("/api/v1/places/" + placeId + "/reviews"))
                .andExpect(jsonPath("$.items[0].helpfulCount").value(2))
                .andExpect(jsonPath("$.items[0].votedByMe").value(true));
        perform(author, get("/api/v1/places/" + placeId + "/reviews"))
                .andExpect(jsonPath("$.items[0].votedByMe").value(false));

        // Bỏ vote không tính → trust tác giả giữ nguyên; bỏ vote tính → trừ lại
        unvote(newcomer, reviewId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulCount").value(1))
                .andExpect(jsonPath("$.votedByMe").value(false));
        assertThat(helpfulVotesCount(author)).isEqualTo(1);
        unvote(trusted, reviewId).andExpect(jsonPath("$.helpfulCount").value(0));
        assertThat(helpfulVotesCount(author)).isZero();
        unvote(trusted, reviewId) // DELETE lần hai: idempotent, không trừ xuống dưới 0
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulCount").value(0));
        assertThat(helpfulVotesCount(author)).isZero();
    }

    @Test
    void helpfulOrderFollowsVotes() throws Exception {
        Account author1 = account(Trust.NEW_VERIFIED);
        Account author2 = account(Trust.NEW_VERIFIED);
        Account voter = account(Trust.NEW_VERIFIED);
        long placeId = approvedPlace();
        long older = review(placeId, author1, "PUBLISHED");
        long newer = review(placeId, author2, "PUBLISHED");

        vote(voter, older).andExpect(status().isOk());

        assertThat(reviewIds(mvc.perform(get("/api/v1/places/" + placeId + "/reviews?sort=HELPFUL"))
                        .andReturn()))
                .containsExactly(older, newer);
    }

    @Test
    void cannotVoteOwnOrInvisibleReviews() throws Exception {
        Account author = account(Trust.TRUSTED);
        Account voter = account(Trust.TRUSTED);
        long placeId = approvedPlace();
        long published = review(placeId, author, "PUBLISHED");

        vote(author, published)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));
        mvc.perform(put("/api/v1/reviews/" + published + "/vote")).andExpect(status().isUnauthorized());

        // Review không hiện với mọi người → như không tồn tại
        for (String status : List.of("PENDING", "REJECTED", "HIDDEN")) {
            long hidden = review(approvedPlace(), account(Trust.NEW_VERIFIED), status);
            vote(voter, hidden)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
        }
        long deleted = review(approvedPlace(), account(Trust.NEW_VERIFIED), "PUBLISHED");
        jdbc.update("UPDATE reviews SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);
        vote(voter, deleted).andExpect(status().isNotFound());
        long onHiddenPlace = review(approvedPlace(), account(Trust.NEW_VERIFIED), "PUBLISHED");
        jdbc.update(
                "UPDATE places SET status = 'HIDDEN' WHERE id = (SELECT place_id FROM reviews WHERE id = ?)",
                onHiddenPlace);
        vote(voter, onHiddenPlace).andExpect(status().isNotFound());
        unvote(voter, onHiddenPlace).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_votes WHERE user_id = ?", Long.class, voter.id()))
                .isZero();
    }

    /** Hai bộ đếm chỉ cộng một lần dù cùng một người bấm nhiều lần đồng thời (INSERT IGNORE + số dòng ảnh hưởng). */
    @Test
    void concurrentVotesFromTheSameUserCountOnce() throws Exception {
        Account author = account(Trust.NEW_VERIFIED);
        Account voter = account(Trust.TRUSTED);
        Account other = account(Trust.TRUSTED);
        long reviewId = review(approvedPlace(), author, "PUBLISHED");

        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> vote(voter, reviewId).andReturn().getResponse().getStatus());
            if (i < 3) {
                calls.add(() -> vote(other, reviewId).andReturn().getResponse().getStatus());
            }
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(calls.size())) {
            for (Future<Integer> f : pool.invokeAll(calls)) {
                assertThat(f.get()).isEqualTo(200);
            }
        }

        assertThat(jdbc.queryForObject("SELECT helpful_count FROM reviews WHERE id = ?", Integer.class, reviewId))
                .isEqualTo(2);
        assertThat(helpfulVotesCount(author)).isEqualTo(2);
    }

    /**
     * A vote review của B đúng lúc B vote review của A (rồi cùng bỏ vote) — mỗi bên cần khóa dòng người dùng của bên kia
     * (khóa ngoại người vote / bộ đếm tác giả). Không khóa theo thứ tự chung thì InnoDB deadlock → 500.
     */
    @Test
    void crossVotingAtTheSameTimeDoesNotDeadlock() throws Exception {
        Account alice = account(Trust.TRUSTED);
        Account bob = account(Trust.TRUSTED);
        long byAlice = review(approvedPlace(), alice, "PUBLISHED");
        long byBob = review(approvedPlace(), bob, "PUBLISHED");

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < 10; round++) {
                for (boolean voting : List.of(true, false)) {
                    List<Callable<Integer>> calls = List.of(
                            () -> (voting ? vote(alice, byBob) : unvote(alice, byBob))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus(),
                            () -> (voting ? vote(bob, byAlice) : unvote(bob, byAlice))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus());
                    for (Future<Integer> f : pool.invokeAll(calls)) {
                        assertThat(f.get()).isEqualTo(200);
                    }
                }
            }
        }
        assertThat(helpfulVotesCount(alice)).isEqualTo(1); // giá trị ban đầu của tài khoản TRUSTED
        assertThat(helpfulVotesCount(bob)).isEqualTo(1);
    }

    /**
     * Hồi quy mất cập nhật: entity được nạp trước khi vote đến (giá trị đếm cũ trong bộ nhớ), sau đó bị sửa và flush —
     * Hibernate ghi lại mọi cột, nên nếu cột đếm còn {@code updatable} thì vote vừa đến bị xóa mất.
     */
    @Test
    void editingReviewOrUserThroughJpaKeepsVotesThatArrivedMeanwhile() throws Exception {
        Account author = account(Trust.NEW_VERIFIED);
        Account voter = account(Trust.TRUSTED);
        long reviewId = review(approvedPlace(), author, "PUBLISHED");

        tx.executeWithoutResult(status -> {
            Review review = entityManager.find(Review.class, reviewId);
            User user = entityManager.find(User.class, author.id());
            assertThat(review.getHelpfulCount()).isZero();
            jdbc.update("UPDATE reviews SET helpful_count = 5 WHERE id = ?", reviewId); // "vote đến cùng lúc"
            jdbc.update("UPDATE users SET helpful_votes_count = 3 WHERE id = ?", author.id());
            review.setContent("Tác giả sửa nội dung trong lúc có người vote hữu ích.");
            user.setDisplayName("Tên mới của tác giả");
        });

        assertThat(jdbc.queryForObject("SELECT helpful_count FROM reviews WHERE id = ?", Integer.class, reviewId))
                .isEqualTo(5);
        assertThat(helpfulVotesCount(author)).isEqualTo(3);
        // Và qua HTTP: tác giả sửa review sau khi có vote → số vote giữ nguyên
        vote(voter, reviewId).andExpect(jsonPath("$.helpfulCount").value(6));
        perform(
                        author,
                        patch("/api/v1/reviews/" + reviewId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rating\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulCount").value(6));
    }

    // ─── Hỗ trợ ─────────────────────────────────────────────────────────────

    private enum Trust {
        /** Đã xác thực, tài khoản mới: trust = base 10. */
        NEW_VERIFIED,
        /** 10 (xác thực) + 18 (tuổi tài khoản trần) + 2 (1 vote hữu ích) = 30 — đúng ngưỡng vote được tính. */
        TRUSTED
    }

    private record Account(long id, String accessToken) {}

    private Account account(Trust trust) throws Exception {
        String email = "vote-" + UUID.randomUUID() + "@localspot.test";
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Người thử vote"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        jdbc.update("UPDATE users SET email_verified_at = UTC_TIMESTAMP(6) WHERE id = ?", id);
        if (trust == Trust.TRUSTED) {
            jdbc.update(
                    "UPDATE users SET created_at = UTC_TIMESTAMP(6) - INTERVAL 200 DAY, helpful_votes_count = 1"
                            + " WHERE id = ?",
                    id);
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
        String slug = "vote-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO places (category_id, created_by, name, slug, address, city, location, status,
                                    created_at, updated_at)
                VALUES (?, ?, 'Quán thử vote', ?, '1 Phố Thử', 'Hà Nội',
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

    private ResultActions vote(Account voter, long reviewId) throws Exception {
        return perform(voter, put("/api/v1/reviews/" + reviewId + "/vote"));
    }

    private ResultActions unvote(Account voter, long reviewId) throws Exception {
        return perform(voter, delete("/api/v1/reviews/" + reviewId + "/vote"));
    }

    private ResultActions perform(Account actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    private int helpfulVotesCount(Account user) {
        Integer count =
                jdbc.queryForObject("SELECT helpful_votes_count FROM users WHERE id = ?", Integer.class, user.id());
        assertThat(count).isNotNull();
        return count;
    }

    private boolean counted(long reviewId, Account voter) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT counted FROM review_votes WHERE review_id = ? AND user_id = ?",
                Boolean.class,
                reviewId,
                voter.id()));
    }

    private static List<Long> reviewIds(MvcResult result) throws Exception {
        List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.items[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
}
