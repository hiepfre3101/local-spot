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

    /**
     * Fallback tìm kiếm khi Meilisearch lỗi (U9, UC08 3b): id địa điểm APPROVED có tên chứa <b>mọi</b> từ trong {@code q}
     * ({@code LIKE '%từ%'}, collation {@code utf8mb4_0900_ai_ci} nên không phân biệt hoa thường / dấu, kể cả {@code đ = d}), cùng bộ lọc và
     * thứ tự như {@link #searchApproved}, phân trang theo vị trí như Meilisearch. Quét bảng — chỉ dùng lúc sự cố.
     */
    List<Long> findApprovedIdsByNameWords(String q, PlaceFilter filter, PlaceSort sort, int offset, int limit);

    /**
     * Địa điểm APPROVED trong bán kính {@code radiusM} quanh (lat, lng), gần nhất trước (FR-11). {@code categoryId} gồm
     * cả danh mục con (cây ≤ 2 cấp).
     */
    List<PlaceDistance> findApprovedWithin(double lat, double lng, int radiusM, Long categoryId, int limit);

    /** Ứng viên nghi trùng (U7): APPROVED + đề xuất PENDING của chính {@code proposerId}, gần nhất trước. */
    List<PlaceDistance> findDuplicateCandidates(double lat, double lng, int radiusM, long proposerId, int limit);

    /**
     * Ứng viên nghi trùng cho kiểm duyệt viên: APPROVED + PENDING <b>của mọi người</b> (kiểm duyệt viên vốn xem được mọi
     * đề xuất chờ), trừ chính địa điểm {@code excludePlaceId}.
     */
    List<PlaceDistance> findModerationDuplicateCandidates(
            double lat, double lng, int radiusM, long excludePlaceId, int limit);
}
