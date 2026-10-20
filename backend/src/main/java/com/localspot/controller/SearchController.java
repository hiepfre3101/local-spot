package com.localspot.controller;

import com.localspot.dto.response.SearchPage;
import com.localspot.repository.PlaceFilter;
import com.localspot.search.SearchSort;
import com.localspot.service.PlaceSearchService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/search} — tìm kiếm tiếng Việt (UC08, FR-09), công khai (khai báo trong {@code SecurityConfig}). Bộ
 * lọc giống {@code GET /places}; lọc theo khoảng cách là UC09 ({@code /places/nearby}).
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final PlaceSearchService searchService;

    public SearchController(PlaceSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchPage search(
            @RequestParam
                    @NotBlank(message = "Nhập từ khóa tìm kiếm.")
                    @Size(max = 100, message = "Từ khóa tối đa 100 ký tự.")
                    String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<Long> amenityIds,
            @RequestParam(required = false) @PositiveOrZero(message = "Giá tối đa không được âm.") Integer priceMax,
            @RequestParam(required = false)
                    @DecimalMin(value = "1", message = "Điểm tối thiểu từ 1 đến 5.")
                    @DecimalMax(value = "5", message = "Điểm tối thiểu từ 1 đến 5.")
                    BigDecimal minRating,
            @RequestParam(defaultValue = "RELEVANCE") SearchSort sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return searchService.search(
                q, new PlaceFilter(categoryId, amenityIds, priceMax, minRating, null), sort, cursor, limit);
    }
}
