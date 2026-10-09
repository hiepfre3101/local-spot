package com.localspot.service;

import com.localspot.dto.response.OwnerReplyResponse;
import com.localspot.dto.response.PhotoResponse;
import com.localspot.dto.response.ReviewResponse;
import com.localspot.entity.OwnerReply;
import com.localspot.entity.Review;
import com.localspot.mapper.ReviewMapper;
import com.localspot.repository.CommentRepository;
import com.localspot.repository.OwnerReplyRepository;
import com.localspot.repository.ReviewVoteRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Dựng {@link ReviewResponse} cho cả trang: vote của người xem, số bình luận, phản hồi của chủ — mỗi loại một truy vấn
 * theo lô, không truy vấn từng review (N+1). Tác giả được nạp sẵn ({@code JOIN FETCH}) ở repository.
 *
 * <p>Ảnh: người khác chỉ thấy ảnh READY, tác giả thấy cả ảnh đang xử lý / lỗi ({@link ReviewPhotoService#photosOf}).
 */
@Component
public class ReviewViews {

    private final ReviewMapper mapper;
    private final ReviewVoteRepository votes;
    private final CommentRepository comments;
    private final OwnerReplyRepository ownerReplies;
    private final ReviewPhotoService photos;

    public ReviewViews(
            ReviewMapper mapper,
            ReviewVoteRepository votes,
            CommentRepository comments,
            OwnerReplyRepository ownerReplies,
            ReviewPhotoService photos) {
        this.mapper = mapper;
        this.votes = votes;
        this.comments = comments;
        this.ownerReplies = ownerReplies;
        this.photos = photos;
    }

    /** {@code viewerId = null}: khách. */
    public List<ReviewResponse> of(List<Review> reviews, Long viewerId) {
        if (reviews.isEmpty()) {
            return List.of();
        }
        List<Long> ids = reviews.stream().map(Review::getId).toList();
        Set<Long> voted = viewerId == null ? Set.of() : new HashSet<>(votes.findVotedReviewIds(viewerId, ids));
        Map<Long, Long> commentCounts = new HashMap<>();
        for (Object[] row : comments.countVisibleByReviewIds(ids)) {
            commentCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        Map<Long, OwnerReply> replies = ownerReplies.findWithAuthorByReviewIdIn(ids).stream()
                .collect(Collectors.toMap(reply -> reply.getReview().getId(), Function.identity()));
        Map<Long, List<PhotoResponse>> photosByReview = photos.photosOf(reviews, viewerId);
        return reviews.stream()
                .map(review -> toResponse(
                        review,
                        viewerId,
                        photosByReview.getOrDefault(review.getId(), List.of()),
                        voted.contains(review.getId()),
                        commentCounts.getOrDefault(review.getId(), 0L),
                        replies.get(review.getId())))
                .toList();
    }

    public ReviewResponse of(Review review, Long viewerId) {
        return of(List.of(review), viewerId).getFirst();
    }

    private ReviewResponse toResponse(
            Review review,
            Long viewerId,
            List<PhotoResponse> photoList,
            boolean votedByMe,
            long commentCount,
            OwnerReply reply) {
        boolean author = review.getUser().getId().equals(viewerId);
        OwnerReplyResponse ownerReply = reply == null ? null : mapper.toOwnerReply(reply);
        return new ReviewResponse(
                review.getId(),
                review.getPlace().getId(),
                mapper.toUserSummary(review.getUser()),
                review.getRating(),
                review.getContent(),
                review.getVisitedAt(),
                review.getStatus(),
                author ? review.getRejectReason() : null, // lý do từ chối chỉ tác giả thấy (openapi)
                photoList,
                review.getHelpfulCount(),
                votedByMe,
                commentCount,
                ownerReply,
                review.getCreatedAt(),
                review.getUpdatedAt(),
                review.getVersion());
    }
}
