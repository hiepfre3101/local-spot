package com.localspot.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * "Tên gần giống" cho cảnh báo nghi trùng (U7, chốt 2026-10-06): chuẩn hóa (chữ thường, bỏ dấu, bỏ ký tự đặc biệt — như
 * slug), bỏ từ chung chung ("quán", "nhà hàng", "cà phê"…), rồi coi là giống nhau nếu một tên chứa tên kia hoặc độ giống
 * theo khoảng cách chỉnh sửa (Levenshtein) ≥ {@value #MIN_SIMILARITY}. Chỉ chạy trên vài địa điểm trong bán kính 50 m nên
 * thuật toán O(n·m) đơn giản là đủ.
 */
public final class PlaceNames {

    static final double MIN_SIMILARITY = 0.8;

    /** Tên ngắn hơn ngưỡng này không dùng luật "chứa" — tránh "an" khớp mọi tên. */
    private static final int MIN_CONTAINED_LENGTH = 4;

    private static final int MAX_NAME_LENGTH = 200;

    /** Từ chung chung (đã chuẩn hóa), so theo ranh giới từ — "ca phe" bị bỏ nhưng "ca kho" thì không. */
    private static final List<Pattern> GENERIC_WORDS =
            List.of("quan an", "nha hang", "ca phe", "cua hang", "quan", "tiem", "cafe", "coffee", "shop").stream()
                    .map(word -> Pattern.compile("\\b" + word + "\\b"))
                    .toList();

    private PlaceNames() {}

    public static boolean similar(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        if (x.isEmpty() || y.isEmpty()) {
            return false;
        }
        if (x.equals(y)) {
            return true;
        }
        boolean xShorter = x.length() <= y.length();
        String shorter = xShorter ? x : y;
        String longer = xShorter ? y : x;
        if (shorter.length() >= MIN_CONTAINED_LENGTH && containsWords(longer, shorter)) {
            return true;
        }
        return similarity(x, y) >= MIN_SIMILARITY;
    }

    /**
     * "Phở Thìn Lò Đúc" → {@code pho thin lo duc}; "Cà phê Giảng" → {@code giang}. Bỏ hết từ chung chung mà còn rỗng
     * ("Quán Cà Phê") thì giữ nguyên tên đã chuẩn hóa.
     */
    static String normalize(String name) {
        String normalized =
                Slugs.slugify(name == null ? "" : name, MAX_NAME_LENGTH).replace('-', ' ');
        String stripped = normalized;
        for (Pattern generic : GENERIC_WORDS) {
            stripped = generic.matcher(stripped).replaceAll(" ");
        }
        stripped = stripped.strip().replaceAll("\\s+", " ");
        return stripped.isEmpty() ? normalized : stripped;
    }

    /** "bun cha" nằm trong "bun cha huong lien" theo nguyên từ — "bun ch" thì không. */
    private static boolean containsWords(String longer, String shorter) {
        return (" " + longer + " ").contains(" " + shorter + " ");
    }

    /** 1 − khoảng cách Levenshtein / độ dài chuỗi dài hơn. */
    static double similarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        return max == 0 ? 1.0 : 1.0 - (double) levenshtein(a, b) / max;
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
