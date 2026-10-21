package com.localspot.service;

import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.search.PlaceSearchIndex;
import com.localspot.search.SearchSort;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpStatus;

/**
 * Cursor của {@code GET /search}: vị trí (offset) trong danh sách kết quả + chế độ dấu, mã hóa {@code SORT|offset|mode}
 * bằng base64url — vẫn là chuỗi mờ với client như mọi cursor khác. {@code mode} = {@code R} khi trang đầu đã chuyển sang
 * tìm không phân biệt dấu (không có kết quả đúng dấu — chốt 2026-10-21): các trang sau phải cùng chế độ, nếu không danh
 * sách lẫn hai thứ tự. Cursor cũ dạng {@code SORT|offset} vẫn đọc được (chế độ đúng dấu).
 *
 * <p>Khác {@code GET /places} (keyset): thứ tự theo độ liên quan không có khóa nào để "tiếp tục sau", nên phân trang
 * theo vị trí như chính Meilisearch. Đánh đổi: địa điểm mới được duyệt giữa hai lần tải có thể làm một kết quả lặp / lệch
 * một vị trí — chấp nhận được với infinite scroll. Cửa sổ tối đa {@link PlaceSearchIndex#MAX_TOTAL_HITS} kết quả.
 */
public final class SearchCursor {

    private SearchCursor() {}

    /** Vị trí + chế độ dấu đọc từ cursor. */
    public record Position(int offset, boolean relaxedAccents) {}

    public static String encode(SearchSort sort, int offset, boolean relaxedAccents) {
        String raw = sort.name() + "|" + offset + (relaxedAccents ? "|R" : "|E");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** {@code null} / rỗng = trang đầu (offset 0). Cursor của thứ tự khác → 422 như {@link PlaceListCursor}. */
    public static Position decode(String cursor, SearchSort expectedSort) {
        if (cursor == null || cursor.isBlank()) {
            return new Position(0, false);
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length < 2 || parts.length > 3 || !parts[0].equals(expectedSort.name())) {
                throw invalid();
            }
            int offset = Integer.parseInt(parts[1]);
            if (offset <= 0 || offset >= PlaceSearchIndex.MAX_TOTAL_HITS) {
                throw invalid();
            }
            if (parts.length == 3 && !parts[2].equals("E") && !parts[2].equals("R")) {
                throw invalid();
            }
            return new Position(offset, parts.length == 3 && parts[2].equals("R"));
        } catch (IllegalArgumentException e) { // gồm NumberFormatException
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.INVALID_CURSOR, "Cursor không hợp lệ.");
    }
}
