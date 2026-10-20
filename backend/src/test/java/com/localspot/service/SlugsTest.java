package com.localspot.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugsTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Phở Thìn Lò Đúc Hà Nội | pho-thin-lo-duc-ha-noi",
                "Cà phê Giảng — 39 Nguyễn Hữu Huân | ca-phe-giang-39-nguyen-huu-huan",
                "ĐẶC SẢN Đà Lạt | dac-san-da-lat",
                "  Bún   chả!!  Hương Liên  | bun-cha-huong-lien",
                "Quán 'Ốc' & Lẩu (24/7) | quan-oc-lau-24-7",
                "Trà sữa ỷ ỹ ữ ự | tra-sua-y-y-u-u",
            })
    void removesVietnameseDiacriticsAndPunctuation(String input, String expected) {
        assertThat(Slugs.slugify(input, 200)).isEqualTo(expected);
    }

    @Test
    void truncatesWithoutTrailingHyphen() {
        // Cắt đúng sau "pho-thin-" phải bỏ dấu gạch cuối
        assertThat(Slugs.slugify("Phở Thìn Lò Đúc", 9)).isEqualTo("pho-thin");
        assertThat(Slugs.slugify("a".repeat(300), 200)).hasSize(200);
    }

    @Test
    void returnsEmptyWhenNoLetterOrDigit() {
        assertThat(Slugs.slugify("!!! — ???", 200)).isEmpty();
    }
}
