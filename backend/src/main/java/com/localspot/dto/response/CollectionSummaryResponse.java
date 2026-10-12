package com.localspot.dto.response;

/**
 * openapi {@code CollectionSummary}. {@code placeCount} / {@code coverPhoto} theo người xem: người khác chỉ tính địa điểm
 * đang hiện; ảnh bìa là ảnh bìa của địa điểm thêm gần nhất.
 */
public record CollectionSummaryResponse(
        Long id, String name, boolean isPublic, String shareSlug, long placeCount, PhotoResponse coverPhoto) {}
