package com.localspot.service;

import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpStatus;

/**
 * Cursor keyset theo id giảm dần ({@code WHERE id < :afterId ORDER BY id DESC LIMIT n}) — dùng chỉ mục khóa chính, chi
 * phí không đổi dù ở trang thứ bao nhiêu, không trùng / sót bản ghi khi dữ liệu được thêm giữa hai lần tải (khác
 * OFFSET). Mã hóa base64url để client coi là chuỗi mờ, không phụ thuộc vào việc cursor là id.
 */
public final class KeysetCursor {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    private KeysetCursor() {}

    public static String encode(long lastId) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(Long.toString(lastId).getBytes(StandardCharsets.US_ASCII));
    }

    /** {@code null} / rỗng = trang đầu. Cursor không giải mã được → 422 (client gửi bừa, không phải lỗi máy chủ). */
    public static Long decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII));
            if (id <= 0) {
                throw invalidCursor();
            }
            return id;
        } catch (IllegalArgumentException e) { // gồm NumberFormatException
            throw invalidCursor();
        }
    }

    /** {@code limit} theo openapi: 1–50, mặc định 20. Ngoài khoảng → 422 thay vì âm thầm cắt. */
    public static int limit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        if (requested < 1 || requested > MAX_LIMIT) {
            throw ApiException.fieldError(
                    ErrorCode.VALIDATION_FAILED, "limit", "limit phải trong khoảng 1–" + MAX_LIMIT + ".");
        }
        return requested;
    }

    private static ApiException invalidCursor() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.INVALID_CURSOR, "Cursor không hợp lệ.");
    }
}
