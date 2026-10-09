package com.localspot.service;

import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpStatus;

/**
 * Cursor keyset của thứ tự HELPFUL ({@code helpful_count, id}) — mã hóa {@code HELPFUL|count|id}. Thứ tự NEWEST dùng
 * {@link KeysetCursor} (chỉ id); cursor của thứ tự này dùng cho thứ tự kia → 422 như {@link PlaceListCursor}.
 */
public final class ReviewListCursor {

    private static final String PREFIX = "HELPFUL";

    private ReviewListCursor() {}

    public record Helpful(int helpfulCount, long id) {}

    public static String encode(Helpful position) {
        String raw = PREFIX + "|" + position.helpfulCount() + "|" + position.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Helpful decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 3 || !parts[0].equals(PREFIX)) {
                throw invalid();
            }
            int helpful = Integer.parseInt(parts[1]);
            long id = Long.parseLong(parts[2]);
            if (helpful < 0 || id <= 0) {
                throw invalid();
            }
            return new Helpful(helpful, id);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.INVALID_CURSOR, "Cursor không hợp lệ.");
    }
}
