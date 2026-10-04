package com.localspot.repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bộ lọc {@code GET /places} (FR-10); trường {@code null} / rỗng = không lọc.
 *
 * @param categoryId danh mục — gồm cả danh mục con (cây hai cấp: chọn "Quán ăn" ra cả "Phở & bún")
 * @param amenityIds địa điểm phải có <b>đủ tất cả</b> tiện ích (AND — thêm tiện ích thì thu hẹp kết quả)
 * @param priceMax ngân sách: địa điểm có mức giá thấp nhất ≤ priceMax; địa điểm chưa khai giá bị loại khi lọc giá
 * @param minRating theo điểm Bayesian hiển thị, không theo trung bình thô
 * @param city so khớp đúng tên thành phố (collation không phân biệt hoa thường / dấu)
 */
public record PlaceFilter(Long categoryId, List<Long> amenityIds, Integer priceMax, BigDecimal minRating, String city) {

    public PlaceFilter {
        amenityIds = amenityIds == null ? List.of() : List.copyOf(amenityIds);
    }
}
