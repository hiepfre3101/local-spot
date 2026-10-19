package com.localspot.dto.response;

/**
 * openapi {@code City} — một thành phố có địa điểm đang hiện ({@code GET /cities}, bộ lọc khu vực của P08 — thêm
 * 2026-10-19). {@code name} khớp đúng {@code places.city}, dùng thẳng cho tham số {@code city} của bảng xếp hạng.
 */
public record CityResponse(String name, long placeCount) {}
