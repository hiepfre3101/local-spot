package com.localspot.search;

import com.localspot.repository.PlaceFilter;

/**
 * Một lần tìm: từ khóa + bộ lọc của {@code GET /places} (trừ {@code city}, openapi {@code /search} không có) + thứ tự +
 * cửa sổ {@code offset} / {@code limit}.
 */
public record PlaceSearchQuery(String q, PlaceFilter filter, SearchSort sort, int offset, int limit) {}
