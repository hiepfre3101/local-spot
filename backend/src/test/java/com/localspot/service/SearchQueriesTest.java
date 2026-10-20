package com.localspot.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchQueriesTest {

    @Test
    void dropsGenericVenueWordsAccentInsensitively() {
        assertThat(SearchQueries.withoutGenericWords("Quán phở Thìn")).isEqualTo("pho thin");
        assertThat(SearchQueries.withoutGenericWords("tiem pho thin")).isEqualTo("pho thin");
        assertThat(SearchQueries.withoutGenericWords("quán ăn ngon")).isEqualTo("ngon");
        assertThat(SearchQueries.withoutGenericWords("Nhà hàng hải sản")).isEqualTo("hai san");
        assertThat(SearchQueries.withoutGenericWords("cửa hàng bánh, shop kem")).isEqualTo("banh kem");
    }

    @Test
    void keepsWhatPeopleSearchFor() {
        // Không có từ chung chung → giữ nguyên văn
        assertThat(SearchQueries.withoutGenericWords("  bún chả  ")).isEqualTo("bún chả");
        assertThat(SearchQueries.withoutGenericWords("寿司")).isEqualTo("寿司");
        // Cà phê là loại quán người dùng tìm — khác danh sách bỏ của U7
        assertThat(SearchQueries.withoutGenericWords("cà phê view đẹp")).isEqualTo("cà phê view đẹp");
        // "quan" trước số là quận
        assertThat(SearchQueries.withoutGenericWords("cơm tấm quận 3")).isEqualTo("cơm tấm quận 3");
        assertThat(SearchQueries.withoutGenericWords("quán bún quan 10")).isEqualTo("bun quan 10");
        // Theo ranh giới từ: "quang", "shopee" không bị cắt
        assertThat(SearchQueries.withoutGenericWords("Quang Trung")).isEqualTo("Quang Trung");
        // Bỏ hết thì rỗng → giữ nguyên
        assertThat(SearchQueries.withoutGenericWords("Quán")).isEqualTo("Quán");
    }
}
