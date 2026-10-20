package com.localspot.repository;

/**
 * Thứ tự danh sách địa điểm ({@code GET /places?sort=}). Mọi thứ tự đều phụ thêm {@code id DESC} để tổng thứ tự là duy
 * nhất — điều kiện để phân trang keyset không trùng / sót khi nhiều địa điểm cùng điểm.
 */
public enum PlaceSort {
    /** Điểm Bayesian giảm dần (mặc định) — không xếp theo trung bình thô (plan §5). */
    SCORE,
    /** Mới nhất: id tăng theo thời điểm tạo nên {@code id DESC} tương đương {@code created_at DESC}. */
    NEWEST,
    MOST_REVIEWED
}
