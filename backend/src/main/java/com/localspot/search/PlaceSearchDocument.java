package com.localspot.search;

import java.math.BigDecimal;
import java.util.List;

/**
 * Một địa điểm trong index Meilisearch. Chỉ giữ trường để <b>tìm, lọc, xếp</b> — kết quả trả về được nạp lại từ MySQL
 * theo id (UC08 bước 4), nên ảnh bìa, giá hiển thị… không cần đồng bộ sang đây và không bao giờ cũ.
 *
 * @param categoryNames danh mục và danh mục cha ("Phở &amp; bún", "Quán ăn") — gõ "quan an" ra cả danh mục con
 * @param categoryIds tương tự, để lọc theo danh mục cha gồm cả con như {@code GET /places}
 * @param priceMin {@code null} khi chưa khai giá — Meilisearch coi như thiếu trường, lọc giá loại ra như MySQL
 */
public record PlaceSearchDocument(
        long id,
        String name,
        List<String> tags,
        List<String> categoryNames,
        String address,
        String city,
        String description,
        List<Long> categoryIds,
        List<Long> amenityIds,
        Integer priceMin,
        BigDecimal bayesianScore,
        int reviewCount) {

    public PlaceSearchDocument {
        tags = List.copyOf(tags);
        categoryNames = List.copyOf(categoryNames);
        categoryIds = List.copyOf(categoryIds);
        amenityIds = List.copyOf(amenityIds);
    }
}
