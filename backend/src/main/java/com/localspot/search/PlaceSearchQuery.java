package com.localspot.search;

import com.localspot.repository.PlaceFilter;
import java.util.List;

/**
 * Một lần tìm: từ khóa + bộ lọc của {@code GET /places} (trừ {@code city}, openapi {@code /search} không có) + thứ tự +
 * cửa sổ {@code offset} / {@code limit}. {@code accentKeys}: mỗi khóa là một từ có dấu kết quả phải chứa <b>đúng dấu</b>
 * (rỗng = không ràng buộc dấu — chốt 2026-10-21).
 */
public record PlaceSearchQuery(
        String q, PlaceFilter filter, List<String> accentKeys, SearchSort sort, int offset, int limit) {

    public PlaceSearchQuery {
        accentKeys = List.copyOf(accentKeys);
    }
}
