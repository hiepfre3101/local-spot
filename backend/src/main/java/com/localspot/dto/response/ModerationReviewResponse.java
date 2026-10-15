package com.localspot.dto.response;

/**
 * openapi {@code ModerationReview} — một mục trong hàng chờ duyệt review (UC28 bước 1): review + thẻ địa điểm + tín hiệu
 * chống review ảo của tác giả. {@code sameSubnetReviewCount24h}: số review khác cùng địa điểm, cùng dải IP, viết trong
 * 24 giờ trước hoặc sau review này (kể cả đã xóa — như luật gắn cờ U3).
 */
public record ModerationReviewResponse(
        ReviewResponse review,
        PlaceSummaryResponse place,
        int authorTrustScore,
        long authorActiveViolations,
        boolean ipFlagged,
        long sameSubnetReviewCount24h) {}
