package com.localspot.event;

import java.util.List;

/**
 * Dữ liệu tìm kiếm của các địa điểm này vừa đổi trong transaction hiện tại → sau commit đồng bộ lại index Meilisearch
 * qua hàng đợi ({@code SearchIndexPublisher}). Phát ở: duyệt địa điểm, chủ sửa thông tin, sửa danh mục; về sau thêm ở
 * tính lại rating, ẩn địa điểm qua báo cáo (UC08 "quy tắc nghiệp vụ").
 *
 * <p>Không cần nói "thêm" hay "xóa": consumer đọc trạng thái hiện tại trong CSDL và tự quyết định.
 */
public record PlaceIndexChangedEvent(List<Long> placeIds) {

    public PlaceIndexChangedEvent {
        placeIds = List.copyOf(placeIds);
    }

    public static PlaceIndexChangedEvent of(Long placeId) {
        return new PlaceIndexChangedEvent(List.of(placeId));
    }
}
