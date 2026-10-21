package com.localspot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccentKeysTest {

    @Test
    void keepsTheExactMarksOfAWord() {
        assertThat(AccentKeys.keyOf("phở")).isEqualTo("pho_8_3");
        assertThat(AccentKeys.keyOf("Phố")).isEqualTo("pho_6_1"); // không phân biệt hoa thường
        assertThat(AccentKeys.keyOf("phở")).isNotEqualTo(AccentKeys.keyOf("phố"));
        assertThat(AccentKeys.keyOf("Đông")).isEqualTo("d_9o_6ng");
        assertThat(AccentKeys.keyOf("ăn")).isEqualTo("a_8an");
        assertThat(AccentKeys.keyOf("pho")).isNull(); // không dấu → không ràng buộc
        assertThat(AccentKeys.keyOf("café")).isEqualTo("cafe_1");
    }

    @Test
    void composedAndDecomposedInputGiveTheSameKey() {
        String decomposed = "phở"; // o + móc + hỏi (gõ kiểu tổ hợp)
        assertThat(AccentKeys.ofTexts(List.of(decomposed))).containsExactly("pho_8_3");
    }

    @Test
    void collectsAccentedWordsOfEveryTextOnce() {
        assertThat(AccentKeys.ofTexts(Arrays.asList("Phở Thìn", null, "13 Lò Đúc, phở bò", "Hà Nội")))
                .containsExactly("bo_2", "d_9u_1c", "ha_2", "lo_2", "no_5_6i", "pho_8_3", "thi_2n");
    }

    @Test
    void queryKeysSkipGenericWordsDroppedFromTheSearch() {
        // "quán" bị bỏ khỏi từ khóa (SearchQueries) — không được ràng buộc kết quả
        assertThat(AccentKeys.ofQuery("quán phở thìn", "pho thin")).containsExactly("pho_8_3", "thi_2n");
        assertThat(AccentKeys.ofQuery("phở bo", "phở bo")).containsExactly("pho_8_3");
        assertThat(AccentKeys.ofQuery("pho thin", "pho thin")).isEmpty();
    }
}
