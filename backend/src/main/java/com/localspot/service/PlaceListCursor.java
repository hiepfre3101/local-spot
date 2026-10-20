package com.localspot.service;

import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.PlaceKeyset;
import com.localspot.repository.PlaceSort;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpStatus;

/**
 * Cursor keyset cho danh sách địa điểm sắp theo khóa tổ hợp ({@code bayesian_score, id}) / ({@code review_count, id}) /
 * ({@code id}). Mã hóa {@code SORT|giá trị|id} bằng base64url — chuỗi mờ với client như {@link KeysetCursor}. Cursor
 * mang theo tên thứ tự: dùng cursor của SCORE cho NEWEST sẽ sai vị trí, nên bị từ chối (422) thay vì trả trang lệch.
 */
public final class PlaceListCursor {

    private PlaceListCursor() {}

    public static String encode(PlaceKeyset keyset) {
        String raw = keyset.sort().name() + "|"
                + (keyset.value() == null ? "" : keyset.value().toPlainString()) + "|" + keyset.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** {@code null} / rỗng = trang đầu. */
    public static PlaceKeyset decode(String cursor, PlaceSort expectedSort) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 3 || !parts[0].equals(expectedSort.name())) {
                throw invalid();
            }
            long id = Long.parseLong(parts[2]);
            if (id <= 0) {
                throw invalid();
            }
            BigDecimal value = expectedSort == PlaceSort.NEWEST ? null : new BigDecimal(parts[1]);
            if (expectedSort == PlaceSort.MOST_REVIEWED) {
                value = BigDecimal.valueOf(value.intValueExact()); // số review là số nguyên
            }
            if (expectedSort == PlaceSort.NEWEST && !parts[1].isEmpty()) {
                throw invalid();
            }
            return new PlaceKeyset(expectedSort, value, id);
        } catch (IllegalArgumentException | ArithmeticException e) { // gồm NumberFormatException
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.INVALID_CURSOR, "Cursor không hợp lệ.");
    }
}
