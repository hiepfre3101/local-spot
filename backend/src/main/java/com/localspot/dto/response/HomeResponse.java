package com.localspot.dto.response;

import java.util.List;

/**
 * openapi {@code HomeResponse} (FR-08): nổi bật (điểm Bayesian), mới nhất, cây danh mục, đánh giá nổi bật ({@code null}
 * khi chưa có review đủ điều kiện). Không có trường theo người xem — cả response dùng chung một bản cache.
 */
public record HomeResponse(
        List<PlaceSummaryResponse> featured,
        List<PlaceSummaryResponse> latest,
        List<CategoryNode> categories,
        HomeReviewResponse featuredReview) {

    public HomeResponse {
        featured = List.copyOf(featured);
        latest = List.copyOf(latest);
        categories = List.copyOf(categories);
    }
}
