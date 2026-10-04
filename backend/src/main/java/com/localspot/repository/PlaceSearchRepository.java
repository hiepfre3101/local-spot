package com.localspot.repository;

import com.localspot.entity.Place;
import java.util.List;

/** Fragment tùy biến của {@link PlaceRepository}: danh sách địa điểm đã duyệt với bộ lọc / thứ tự động. */
public interface PlaceSearchRepository {

    /**
     * Địa điểm APPROVED theo bộ lọc, keyset sau {@code after} ({@code null} = trang đầu), kèm danh mục (fetch join — quan
     * hệ ManyToOne nên LIMIT vẫn chạy ở SQL).
     */
    List<Place> searchApproved(PlaceFilter filter, PlaceSort sort, PlaceKeyset after, int limit);
}
