package com.localspot.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * openapi {@code PlaceSummary} — thẻ địa điểm trong danh sách. {@code bayesianScore} là điểm xếp hạng hiển thị,
 * {@code avgRating} là trung bình thô (S3). {@code distanceM} chỉ có ở {@code /places/nearby}. {@code tags} theo bảng
 * chữ cái — thẻ hiện tối đa 3 là việc của giao diện (SpotCard), API trả đủ. {@code city} thêm 2026-10-21: giao diện hiện
 * địa chỉ kèm thành phố ("48 Cách Mạng Tháng Tám, TP. Hồ Chí Minh") ở mọi danh sách, không riêng trang chi tiết.
 */
public record PlaceSummaryResponse(
        Long id,
        String slug,
        String name,
        CategoryRef category,
        String address,
        String city,
        GeoPoint location,
        PhotoResponse coverPhoto,
        Integer priceMin,
        Integer priceMax,
        BigDecimal bayesianScore,
        BigDecimal avgRating,
        int reviewCount,
        String status,
        Integer distanceM,
        List<String> tags) {

    public PlaceSummaryResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
