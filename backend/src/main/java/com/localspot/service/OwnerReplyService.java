package com.localspot.service;

import com.localspot.dto.response.OwnerReplyResponse;
import com.localspot.entity.OwnerReply;
import com.localspot.entity.PlaceStatus;
import com.localspot.entity.Review;
import com.localspot.entity.ReviewStatus;
import com.localspot.entity.User;
import com.localspot.mapper.ReviewMapper;
import com.localspot.repository.OwnerReplyRepository;
import com.localspot.repository.ReviewRepository;
import com.localspot.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chủ địa điểm phản hồi review (UC25, FR-33) — một phản hồi / review, sửa được (D6). Quyền "là chủ hiện tại của địa điểm
 * chứa review" kiểm ở {@code @PreAuthorize} ({@code review:reply-own-place}). Luật (chốt 2026-10-16):
 *
 * <ul>
 *   <li>Chỉ phản hồi review đang hiện công khai (PUBLISHED trên địa điểm APPROVED) — review khác → 404, như vote / bình
 *       luận. Xóa thì không cần review còn hiện: review bị ẩn sau đó, chủ vẫn gỡ được phản hồi của mình.
 *   <li>Tạo / sửa chung một {@code PUT}; khóa dòng review để hai lần bấm đồng thời không đụng {@code UNIQUE(review_id)}.
 *   <li>Chủ hiện tại sửa phản hồi của chủ trước → phản hồi đứng tên chủ hiện tại ({@code user_id} = "chủ tại thời điểm
 *       phản hồi").
 *   <li>Xóa là xóa hẳn (bảng không có xóa mềm — CLAUDE.md §3 chỉ yêu cầu với places / reviews / comments / users); xóa
 *       khi chưa có phản hồi vẫn 204 (idempotent, như bỏ vote).
 * </ul>
 *
 * Thông báo cho tác giả review (FR-33) gắn ở mục thông báo.
 */
@Service
public class OwnerReplyService {

    private final OwnerReplyRepository replies;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final ReviewMapper mapper;

    public OwnerReplyService(
            OwnerReplyRepository replies, ReviewRepository reviews, UserRepository users, ReviewMapper mapper) {
        this.replies = replies;
        this.reviews = reviews;
        this.users = users;
        this.mapper = mapper;
    }

    @Transactional
    public OwnerReplyResponse upsert(Long ownerId, Long reviewId, String content) {
        Review review = reviews.findByIdForUpdate(reviewId).orElseThrow(ReviewService::reviewNotFound);
        if (review.getStatus() != ReviewStatus.PUBLISHED || review.getPlace().getStatus() != PlaceStatus.APPROVED) {
            throw ReviewService.reviewNotFound();
        }
        User owner = users.getReferenceById(ownerId);
        String text = content.strip();
        OwnerReply reply = replies.findByReviewId(reviewId).orElse(null);
        if (reply == null) {
            reply = new OwnerReply(review, owner, text);
        } else {
            reply.setContent(text);
            reply.setUser(owner);
        }
        return mapper.toOwnerReply(replies.saveAndFlush(reply)); // flush → updatedAt mới trong response
    }

    @Transactional
    public void delete(Long reviewId) {
        reviews.findByIdForUpdate(reviewId).orElseThrow(ReviewService::reviewNotFound);
        replies.findByReviewId(reviewId).ifPresent(replies::delete);
    }
}
