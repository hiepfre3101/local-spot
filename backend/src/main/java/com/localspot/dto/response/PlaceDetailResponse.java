package com.localspot.dto.response;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * openapi {@code PlaceDetail} = {@code PlaceSummary} + chi tiết (FR-13). Trường theo người xem: {@code claimable},
 * {@code myReviewId}, {@code rejectReason} (chỉ người đề xuất / kiểm duyệt viên). {@code ratingDistribution} đếm review
 * PUBLISHED theo số sao, đủ 5 khóa 1–5.
 */
public record PlaceDetailResponse(
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
        Integer distanceM,
        String description,
        String city,
        String phone,
        String website,
        List<PhotoResponse> photos,
        List<OpeningHourResponse> openingHours,
        List<AmenityResponse> amenities,
        List<String> tags,
        Map<Integer, Long> ratingDistribution,
        int checkinCount,
        UserSummary owner,
        boolean claimable,
        Long myReviewId,
        String rejectReason,
        int version) {

    public PlaceDetailResponse {
        photos = List.copyOf(photos);
        openingHours = List.copyOf(openingHours);
        amenities = List.copyOf(amenities);
        tags = List.copyOf(tags);
        // TreeMap: JSON giữ thứ tự 1 → 5
        ratingDistribution = Collections.unmodifiableMap(new TreeMap<>(ratingDistribution));
    }
}
