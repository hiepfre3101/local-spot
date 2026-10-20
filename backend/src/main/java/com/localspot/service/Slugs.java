package com.localspot.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sinh slug ASCII từ tiếng Việt: "Phở Thìn Lò Đúc" → {@code pho-thin-lo-duc}. Bỏ dấu bằng chuẩn hóa Unicode NFD (tách
 * chữ và dấu) rồi xóa dấu kết hợp; riêng {@code đ/Đ} là chữ riêng (không tách được) nên đổi tay.
 */
public final class Slugs {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");

    private Slugs() {}

    /** Slug tối đa {@code maxLength} ký tự, không bắt đầu / kết thúc bằng {@code -}; có thể rỗng nếu không có chữ / số. */
    public static String slugify(String text, int maxLength) {
        String ascii = COMBINING_MARKS
                .matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
                .replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        String slug =
                EDGE_HYPHENS.matcher(NON_ALNUM.matcher(ascii).replaceAll("-")).replaceAll("");
        if (slug.length() > maxLength) {
            slug = EDGE_HYPHENS.matcher(slug.substring(0, maxLength)).replaceAll("");
        }
        return slug;
    }
}
