package com.localspot.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Khóa giữ dấu của một từ tiếng Việt — để tìm <b>đúng dấu</b> khi người dùng gõ có dấu (chốt 2026-10-21): "phở" không còn
 * ra "phố". Meilisearch bỏ dấu cả khi lập chỉ mục lẫn khi so khớp (nhờ vậy "pho" ra "Phở"), nên thông tin dấu được giữ ở
 * một trường <b>chỉ để lọc</b> ({@code accentKeys}) chứ không đổi cách tìm.
 *
 * <p>Khóa = chữ cái gốc + mã từng dấu theo kiểu gõ VNI, ngăn bằng {@code _}: "phở" → {@code pho_8_3} (ơ = o + móc, hỏi),
 * "phố" → {@code pho_6_1}, "đông" → {@code d_9o_6ng}. Chỉ chứa ASCII nên không phụ thuộc cách Meilisearch chuẩn hóa chuỗi
 * khi so bộ lọc; dấu {@code _} không bao giờ có trong từ (từ được tách theo ký tự không phải chữ / số) nên hai từ khác
 * nhau không ra cùng khóa. Chỉ từ <b>có dấu</b> mới có khóa — từ gõ không dấu không bị ràng buộc gì thêm.
 */
public final class AccentKeys {

    private static final Pattern WORD_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

    private AccentKeys() {}

    /** Khóa của mọi từ có dấu trong các đoạn văn (tên, thẻ, địa chỉ…), không trùng, theo thứ tự chữ cái. */
    public static List<String> ofTexts(Collection<String> texts) {
        Set<String> keys = new TreeSet<>();
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            for (String word : words(text)) {
                String key = keyOf(word);
                if (key != null) {
                    keys.add(key);
                }
            }
        }
        return List.copyOf(keys);
    }

    /**
     * Khóa của các từ có dấu người dùng gõ, <b>chỉ những từ còn lại</b> sau khi bỏ từ chung chung ({@code searchedQ} — bản
     * gửi Meilisearch, có thể đã bỏ dấu): "quán phở" → chỉ {@code phở}; "quán" không ràng buộc kết quả.
     */
    public static List<String> ofQuery(String rawQ, String searchedQ) {
        if (rawQ == null || searchedQ == null) {
            return List.of();
        }
        Set<String> searchedWords = new TreeSet<>();
        for (String word : words(searchedQ)) {
            searchedWords.add(Slugs.slugify(word, 100));
        }
        Set<String> keys = new TreeSet<>();
        for (String word : words(rawQ)) {
            String key = keyOf(word);
            if (key != null && searchedWords.contains(Slugs.slugify(word, 100))) {
                keys.add(key);
            }
        }
        return List.copyOf(keys);
    }

    /** {@code null} khi từ không có dấu nào. */
    static String keyOf(String word) {
        String decomposed = Normalizer.normalize(word.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder key = new StringBuilder(decomposed.length() + 6);
        boolean accented = false;
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            String mark = markCode(c);
            if (mark != null) {
                key.append('_').append(mark);
                accented = true;
            } else if (c == 'đ') {
                key.append("d_9");
                accented = true;
            } else {
                key.append(c);
            }
        }
        return accented ? key.toString() : null;
    }

    /** Mã dấu kiểu VNI; dấu ngoài tiếng Việt (vd. "é" của tên nước ngoài vẫn là U+0301) dùng mã điểm Unicode. */
    private static String markCode(char c) {
        return switch (c) {
            case '́' -> "1"; // sắc
            case '̀' -> "2"; // huyền
            case '̉' -> "3"; // hỏi
            case '̃' -> "4"; // ngã
            case '̣' -> "5"; // nặng
            case '̂' -> "6"; // mũ: â ê ô
            case '̆' -> "8a"; // trăng: ă
            case '̛' -> "8"; // móc: ơ ư
            default -> Character.getType(c) == Character.NON_SPACING_MARK ? "u" + Integer.toHexString(c) : null;
        };
    }

    private static List<String> words(String text) {
        List<String> result = new ArrayList<>();
        for (String word : WORD_SEPARATOR.split(Normalizer.normalize(text, Normalizer.Form.NFC))) {
            if (!word.isEmpty()) {
                result.add(word);
            }
        }
        return result;
    }
}
