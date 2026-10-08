package com.localspot.dto.response;

import java.util.List;

/**
 * Trang kết quả {@code GET /search}: như {@link CursorPage} kèm {@code degraded = true} khi Meilisearch lỗi và kết quả
 * đến từ fallback MySQL {@code LIKE} trên tên (U9) — frontend có thể báo "kết quả có thể chưa đầy đủ".
 */
public record SearchPage(List<PlaceSummaryResponse> items, String nextCursor, boolean degraded) {

    public SearchPage {
        items = List.copyOf(items);
    }
}
