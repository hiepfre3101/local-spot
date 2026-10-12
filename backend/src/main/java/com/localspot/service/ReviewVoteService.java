package com.localspot.service;

import com.localspot.config.TrustProperties;
import com.localspot.dto.response.VoteResultResponse;
import com.localspot.entity.Review;
import com.localspot.entity.ReviewStatus;
import com.localspot.entity.User;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.ReviewRepository;
import com.localspot.repository.ReviewVoteRepository;
import com.localspot.repository.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vote "hữu ích" (UC14, FR-20) — PUT / DELETE idempotent, mỗi người một vote / review ({@code PK(review_id, user_id)}).
 *
 * <p><b>Hai bộ đếm</b> (database.md §4), đều cập nhật bằng {@code UPDATE ± 1} nguyên tử, không qua entity:
 *
 * <ul>
 *   <li>{@code reviews.helpful_count} — mọi vote, để hiển thị và xếp thứ tự HELPFUL.
 *   <li>{@code users.helpful_votes_count} của tác giả — chỉ vote {@code counted}: người vote có trust ≥ 30 <b>tại thời
 *       điểm vote</b> (requirements §5.1, chụp lại, không tính lại về sau). Tránh vòng lặp "trust của tôi phụ thuộc trust
 *       của người vote cho tôi", và tài khoản mới / rác vote cho nhau không đẩy được trust.
 * </ul>
 *
 * Mỗi bộ đếm chỉ đổi khi dòng vote thật sự được thêm / xóa (số dòng ảnh hưởng của INSERT IGNORE / DELETE) → hai request
 * chen nhau không bao giờ cộng / trừ hai lần.
 */
@Service
public class ReviewVoteService {

    private final ReviewVoteRepository votes;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final TrustScoreService trust;
    private final TrustProperties trustProperties;
    private final Clock clock;

    public ReviewVoteService(
            ReviewVoteRepository votes,
            ReviewRepository reviews,
            UserRepository users,
            TrustScoreService trust,
            TrustProperties trustProperties,
            Clock clock) {
        this.votes = votes;
        this.reviews = reviews;
        this.users = users;
        this.trust = trust;
        this.trustProperties = trustProperties;
        this.clock = clock;
    }

    /** Chỉ vote review đang hiện (404 nếu không); không vote review của mình (409, chốt 2026-10-11). */
    @Transactional
    public VoteResultResponse vote(Long userId, Long reviewId) {
        Review review = reviews.findVisibleById(reviewId).orElseThrow(ReviewService::reviewNotFound);
        Long authorId = review.getUser().getId();
        if (authorId.equals(userId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.SELF_ACTION_FORBIDDEN,
                    "Không thể vote hữu ích cho đánh giá của chính mình.");
        }
        User voter = lockInOrder(userId, authorId, reviewId).get(userId);
        boolean counted = trust.trustScoreOf(voter) >= trustProperties.publishThreshold();
        if (votes.insertIfAbsent(reviewId, userId, counted, clock.instant()) == 1) {
            reviews.incrementHelpfulCount(reviewId);
            if (counted) {
                users.incrementHelpfulVotesCount(authorId);
            }
        }
        return new VoteResultResponse(reviews.findHelpfulCount(reviewId), true);
    }

    /** Bỏ vote — chưa vote thì vẫn 200 (idempotent). Trừ trust của tác giả chỉ khi vote bị bỏ là vote {@code counted}. */
    @Transactional
    public VoteResultResponse unvote(Long userId, Long reviewId) {
        Review review = reviews.findVisibleById(reviewId).orElseThrow(ReviewService::reviewNotFound);
        Long authorId = review.getUser().getId();
        lockInOrder(userId, authorId, reviewId);
        if (votes.deleteVote(reviewId, userId, true) == 1) {
            reviews.decrementHelpfulCount(reviewId);
            users.decrementHelpfulVotesCount(authorId);
        } else if (votes.deleteVote(reviewId, userId, false) == 1) {
            reviews.decrementHelpfulCount(reviewId);
        }
        return new VoteResultResponse(reviews.findHelpfulCount(reviewId), false);
    }

    /**
     * Khóa độc quyền trước mọi dòng sẽ đụng tới, theo một thứ tự chung: hai dòng {@code users} (id tăng dần) rồi dòng
     * {@code reviews}. Không có bước này, InnoDB deadlock (bắt được bằng test đồng thời):
     *
     * <ul>
     *   <li>INSERT vào {@code review_votes} giữ khóa <b>chia sẻ</b> trên dòng cha {@code reviews} / {@code users} (kiểm
     *       tra khóa ngoại), rồi UPDATE bộ đếm cần khóa <b>độc quyền</b> trên chính dòng đó — hai vote cùng lúc cùng giữ
     *       S, cùng chờ X.
     *   <li>A vote review của B trong khi B vote review của A: mỗi bên giữ S trên dòng người dùng của bên kia (người
     *       vote) và chờ X trên đó (tác giả).
     * </ul>
     *
     * Khi đã giữ X, kiểm tra khóa ngoại trên dòng của chính mình không phải chờ. Đánh đổi: vote cho cùng một tác giả
     * xếp hàng nối tiếp — mỗi lượt vài truy vấn, không đáng kể ở quy mô này. Trạng thái review đọc lại sau khi khóa: bị
     * ẩn / xóa ngay trước đó thì 404.
     */
    private Map<Long, User> lockInOrder(Long voterId, Long authorId, Long reviewId) {
        Map<Long, User> locked = users.findAllByIdForUpdate(new TreeSet<>(List.of(voterId, authorId))).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        if (!locked.containsKey(voterId)) {
            throw userNotFound();
        }
        reviews.lockStatusById(reviewId)
                .filter(status -> status.equals(ReviewStatus.PUBLISHED.name()))
                .orElseThrow(ReviewService::reviewNotFound);
        return locked;
    }

    private static ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng.");
    }
}
