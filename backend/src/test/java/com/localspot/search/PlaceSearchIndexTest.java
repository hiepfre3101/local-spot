package com.localspot.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.localspot.repository.PlaceFilter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PlaceSearchIndexTest {

    @Test
    void synonymGroupsBecomeMutualSynonyms() {
        Map<String, String[]> synonyms = PlaceSearchIndex.loadSynonyms(JsonMapper.shared());

        assertThat(synonyms.get("cafe")).containsExactlyInAnyOrder("cà phê", "coffee");
        assertThat(synonyms.get("cà phê")).containsExactlyInAnyOrder("cafe", "coffee");
        assertThat(synonyms.get("sài gòn")).contains("hồ chí minh", "hcm", "saigon");
        assertThat(synonyms).allSatisfy((term, others) -> assertThat(others).doesNotContain(term));
    }

    @Test
    void filterMatchesPlaceListSemantics() {
        PlaceFilter filter = new PlaceFilter(7L, List.of(3L, 5L, 3L), 50000, new BigDecimal("4.5"), "Hà Nội");

        assertThat(PlaceSearchIndex.filterOf(filter))
                .containsExactly(
                        "categoryIds = 7", // gồm danh mục con (tài liệu mang cả id danh mục cha)
                        "amenityIds = 3", // đủ tất cả tiện ích, không lặp
                        "amenityIds = 5",
                        "priceMin <= 50000",
                        "bayesianScore >= 4.5");
        assertThat(PlaceSearchIndex.filterOf(new PlaceFilter(null, null, null, null, null)))
                .isEmpty();
    }

    @Test
    void everySortEndsWithIdForAStableOrder() {
        assertThat(PlaceSearchIndex.sortOf(SearchSort.RELEVANCE)).isNull();
        assertThat(PlaceSearchIndex.sortOf(SearchSort.SCORE)).containsExactly("bayesianScore:desc", "id:desc");
        assertThat(PlaceSearchIndex.sortOf(SearchSort.NEWEST)).containsExactly("id:desc");
        assertThat(PlaceSearchIndex.sortOf(SearchSort.MOST_REVIEWED)).containsExactly("reviewCount:desc", "id:desc");
    }
}
