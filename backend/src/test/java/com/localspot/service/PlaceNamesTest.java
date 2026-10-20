package com.localspot.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** U7 — "tên gần giống" (chốt 2026-10-06): chuẩn hóa + bỏ từ chung chung + chứa nguyên từ / Levenshtein ≥ 0.8. */
class PlaceNamesTest {

    @Test
    void normalizesCaseDiacriticsPunctuationAndGenericWords() {
        assertThat(PlaceNames.normalize("Phở Thìn — Lò Đúc!")).isEqualTo("pho thin lo duc");
        assertThat(PlaceNames.normalize("Cà phê Giảng")).isEqualTo("giang");
        assertThat(PlaceNames.normalize("Giảng Coffee")).isEqualTo("giang");
        assertThat(PlaceNames.normalize("Nhà hàng Ngon")).isEqualTo("ngon");
        // "ca" chỉ bị bỏ trong cụm "ca phe" — "Cá kho làng Vũ Đại" giữ nguyên
        assertThat(PlaceNames.normalize("Cá kho làng Vũ Đại")).isEqualTo("ca kho lang vu dai");
        // Toàn từ chung chung → giữ tên đã chuẩn hóa thay vì rỗng
        assertThat(PlaceNames.normalize("Quán Cà Phê")).isEqualTo("quan ca phe");
    }

    @Test
    void sameNameWrittenDifferentlyIsSimilar() {
        assertThat(PlaceNames.similar("Phở Thìn", "pho thin")).isTrue();
        assertThat(PlaceNames.similar("Cà phê Giảng", "Giảng Coffee")).isTrue();
        assertThat(PlaceNames.similar("Quán trà sữa Bếp Nhà", "Trà sữa Bếp Nhà"))
                .isTrue();
    }

    @Test
    void oneNameContainingTheOtherByWholeWordsIsSimilar() {
        assertThat(PlaceNames.similar("Bún chả", "Bún chả Hương Liên")).isTrue();
        assertThat(PlaceNames.similar("Phở Thìn Lò Đúc", "Phở Thìn")).isTrue();
        // Không khớp nửa từ
        assertThat(PlaceNames.similar("Bún ch", "Bún chả Hương Liên")).isFalse();
        // Tên quá ngắn không dùng luật "chứa"
        assertThat(PlaceNames.similar("An", "An Cafe Phố Cổ")).isFalse();
    }

    @Test
    void typosAreToleratedButDifferentPlacesAreNot() {
        assertThat(PlaceNames.similar("Bánh mì Phượng", "Banh mi Phuongg")).isTrue();
        assertThat(PlaceNames.similar("Phở Thìn", "Phở Lý")).isFalse();
        assertThat(PlaceNames.similar("Bún bò Huế O Xuân", "Cơm tấm Sài Gòn")).isFalse();
        assertThat(PlaceNames.similar("", "Phở Thìn")).isFalse();
        assertThat(PlaceNames.similarity("abcde", "abcdx")).isEqualTo(0.8);
    }
}
