package com.localspot.service;

import com.localspot.dto.response.CursorPage;
import java.util.List;
import java.util.function.Function;

/** Cắt kết quả "lấy dư một dòng" thành {@link CursorPage} với cursor là id dòng cuối ({@link KeysetCursor}). */
final class KeysetPages {

    private KeysetPages() {}

    /** {@code rows} được truy vấn với {@code LIMIT pageSize + 1}: có dòng dư nghĩa là còn trang sau, không cần COUNT. */
    static <E, R> CursorPage<R> byId(
            List<E> rows, int pageSize, Function<E, Long> idOf, Function<List<E>, List<R>> map) {
        boolean hasMore = rows.size() > pageSize;
        List<E> page = hasMore ? rows.subList(0, pageSize) : rows;
        return new CursorPage<>(map.apply(page), hasMore ? KeysetCursor.encode(idOf.apply(page.getLast())) : null);
    }
}
