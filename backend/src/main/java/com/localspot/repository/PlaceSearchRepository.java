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
