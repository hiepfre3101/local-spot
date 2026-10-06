package com.localspot.dto.response;

import java.math.BigDecimal;

/**
 * openapi {@code PlaceSummary} — thẻ địa điểm trong danh sách. {@code bayesianScore} là điểm xếp hạng hiển thị,
 * {@code avgRating} là trung bình thô (S3). {@code distanceM} chỉ có ở {@code /places/nearby}.
 */
public record PlaceSummaryResponse(
        Long id,
        String slug,
        String name,
        CategoryRef category,
        String address,
        GeoPoint location,
        PhotoResponse coverPhoto,
        Integer priceMin,
        Integer priceMax,
        BigDecimal bayesianScore,
        BigDecimal avgRating,
        int reviewCount,
        String status,
        Integer distanceM) {}
