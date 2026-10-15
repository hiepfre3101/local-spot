package com.localspot.dto.response;

import com.localspot.entity.ReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * openapi {@code ModerationReviewDetail} — màn duyệt một review (UC28 bước 2): mục hàng chờ, lịch sử tác giả (ngày tham
 * gia, số review theo trạng thái — không tính review đã xóa) và các review khác cùng dải IP trên địa điểm (chưa xóa, tối
 * đa 20, cũ nhất trước).
 */
public record ModerationReviewDetailResponse(
        ModerationReviewResponse item,
        Instant authorJoinedAt,
        Map<ReviewStatus, Long> authorReviewCounts,
        List<ReviewResponse> sameSubnetReviews) {

    public ModerationReviewDetailResponse {
        authorReviewCounts = Map.copyOf(authorReviewCounts);
        sameSubnetReviews = List.copyOf(sameSubnetReviews);
    }
}
