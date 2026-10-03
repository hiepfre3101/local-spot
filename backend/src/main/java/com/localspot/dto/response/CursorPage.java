package com.localspot.dto.response;

import java.util.List;

/**
 * Trang kết quả phân trang cursor (quy ước chung của openapi): {@code nextCursor = null} là hết. Cursor là chuỗi mờ —
 * frontend chỉ gửi lại nguyên văn, không tự dựng.
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

    public CursorPage {
        items = List.copyOf(items);
    }
}
