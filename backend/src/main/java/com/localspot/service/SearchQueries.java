package com.localspot.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chuẩn bị từ khóa trước khi gửi Meilisearch: bỏ từ chỉ loại hình quán ("quán", "tiệm", "nhà hàng"…) — chốt 2026-10-08.
 *
 * <p>Lý do: Meilisearch ({@code matchingStrategy = last}) chỉ bỏ bớt từ khi <b>không</b> tài liệu nào khớp đủ, và bỏ từ
 * cuối trước. "quán phở thìn" — tên "Phở Thìn" không có chữ "quán", nhưng mô tả "Quán…" của chỗ khác thì có, nên chỗ đó
 * khớp đủ và Phở Thìn bị bỏ hẳn khỏi kết quả (đo trên dữ liệu thử). Bỏ từ chung chung thì tìm "phở thìn" như ý người gõ.
 *
 * <p>Cùng cách chuẩn hóa với U7 ({@link PlaceNames}: chữ thường, bỏ dấu, theo ranh giới từ), nhưng danh sách khác:
 * <b>giữ</b> "cà phê" / "cafe" / "coffee" — người dùng tìm đúng loại quán đó; giữ "quan" đứng trước số ("quan 3" là quận
 * 3). Bỏ hết mà rỗng ("quán") thì gửi nguyên từ khóa. Meilisearch tự bỏ dấu khi so khớp nên gửi bản đã chuẩn hóa không
 * mất gì.
 */
public final class SearchQueries {

    private static final int MAX_LENGTH = 200;

    private static final Pattern GENERIC_WORDS =
            Pattern.compile("\\b(quan an|nha hang|cua hang|quan(?! \\d)|tiem|shop)\\b");

    private SearchQueries() {}

    /**
     * "Quán phở Thìn" → {@code pho thin}; "quận 3", "bún chả" (không có từ chung chung) → giữ nguyên; "Quán" → giữ
     * nguyên. Chỉ trả bản chuẩn hóa khi thật sự bỏ được từ — từ khóa khác giữ nguyên văn (kể cả chữ ngoài bảng Latin).
     */
    public static String withoutGenericWords(String q) {
        String normalized = Slugs.slugify(q, MAX_LENGTH).replace('-', ' ');
        Matcher generic = GENERIC_WORDS.matcher(normalized);
        if (!generic.find()) {
            return q.strip();
        }
        String stripped = generic.replaceAll(" ").strip().replaceAll("\\s+", " ");
        return stripped.isEmpty() ? q.strip() : stripped;
    }
}
