package com.localspot.service;

import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;

/** Keyset tổ hợp {@code (thời điểm theo dõi, id người kia)} — như {@link ReviewListCursor}; chuỗi mờ với client. */
public final class FollowCursor {

    private static final String PREFIX = "FOLLOW";

    private FollowCursor() {}

    public record Position(Instant at, long otherId) {}

    public static String encode(Position position) {
        String raw = PREFIX + "|" + position.at().getEpochSecond() + "|"
                + position.at().getNano() + "|" + position.otherId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Position decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 4 || !parts[0].equals(PREFIX)) {
                throw invalid();
            }
            Instant at = Instant.ofEpochSecond(Long.parseLong(parts[1]), Long.parseLong(parts[2]));
            long otherId = Long.parseLong(parts[3]);
            if (otherId <= 0) {
                throw invalid();
            }
            return new Position(at, otherId);
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.INVALID_CURSOR, "Cursor không hợp lệ.");
    }
}
