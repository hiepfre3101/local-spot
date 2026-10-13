package com.localspot.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * openapi {@code CollectionDetail}. Chủ thấy cả địa điểm đã bị ẩn sau khi thêm ({@code place.status} khác
 * {@code APPROVED}) để tự bỏ ra; người khác không thấy (chốt 2026-10-12).
 */
public record CollectionDetailResponse(
        Long id,
        String name,
        boolean isPublic,
        String shareSlug,
        long placeCount,
        PhotoResponse coverPhoto,
        String description,
        UserSummary owner,
        List<Item> places) {

    public CollectionDetailResponse {
        places = List.copyOf(places);
    }

    public record Item(PlaceSummaryResponse place, String note, Instant addedAt) {}
}
