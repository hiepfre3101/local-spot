package com.localspot.service;

import com.localspot.entity.ModerationItemType;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.ModerationQueueKey;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;

/**
 * Cursor của hàng chờ gộp — mã hóa {@code QUEUE|submittedAt|type|sortId} (thời điểm ISO-8601 giữ đủ micro giây của cột
 * {@code DATETIME(6)}, nếu làm tròn sẽ lặp / sót mục cùng giây). Cursor của danh sách khác → 422 như
 * {@link ReviewListCursor}.
 */
public final class ModerationQueueCursor {

    private static final String PREFIX = "QUEUE";

    private ModerationQueueCursor() {}

    public static String encode(ModerationQueueKey key) {
        String raw = PREFIX + "|" + key.submittedAt() + "|" + key.type() + "|" + key.sortId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static ModerationQueueKey decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 4 || !parts[0].equals(PREFIX)) {
                throw invalid();
            }
            long sortId = Long.parseLong(parts[3]);
            if (sortId <= 0) {
                throw invalid();
            }
            return new ModerationQueueKey(Instant.parse(parts[1]), ModerationItemType.valueOf(parts[2]), sortId);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.INVALID_CURSOR, "Cursor không hợp lệ.");
    }
}
