package com.localspot.dto.response;

import java.util.List;

/**
 * openapi {@code HomeResponse} (FR-08): nổi bật (điểm Bayesian), mới nhất, cây danh mục, đánh giá nổi bật ({@code null}
 * khi chưa có review đủ điều kiện), ảnh trang chủ đang bật ({@code banners} — UC34; ô không có = hình minh hoạ mặc định).
 * Không có trường theo người xem — cả response dùng chung một bản cache.
 */
public record HomeResponse(
        List<PlaceSummaryResponse> featured,
        List<PlaceSummaryResponse> latest,
        List<CategoryNode> categories,
        HomeReviewResponse featuredReview,
        List<HomeBannerResponse> banners) {

    public HomeResponse {
        featured = List.copyOf(featured);
        latest = List.copyOf(latest);
        categories = List.copyOf(categories);
        // Bản cache cũ (trước khi có trường này) đọc ra null
        banners = banners == null ? List.of() : List.copyOf(banners);
    }
}
