package com.localspot.dto.response;

import java.util.List;

/**
 * Trang kết quả {@code GET /search}: như {@link CursorPage} kèm {@code degraded = true} khi Meilisearch lỗi và kết quả
 * đến từ fallback MySQL {@code LIKE} trên tên (U9) — frontend có thể báo "kết quả có thể chưa đầy đủ".
 * {@code relaxedAccents = true}: từ khóa có dấu nhưng không địa điểm nào khớp đúng dấu, kết quả là bản không phân biệt
 * dấu (chốt 2026-10-21) — frontend báo "không có kết quả đúng dấu".
 */
public record SearchPage(
        List<PlaceSummaryResponse> items, String nextCursor, boolean degraded, boolean relaxedAccents) {

    public SearchPage {
        items = List.copyOf(items);
    }
}
