package com.localspot.search;

/**
 * Thứ tự kết quả {@code GET /search?sort=} (UC08 bước 5). Ngoài độ liên quan, các thứ tự còn lại giống {@code GET /places}
 * và cùng phụ thêm {@code id} giảm dần để thứ tự là duy nhất.
 */
public enum SearchSort {
    /** Độ liên quan (mặc định): khớp nhiều từ, ít lỗi chính tả, khớp ở tên… — điểm Bayesian chỉ phân định khi ngang nhau. */
    RELEVANCE,
    SCORE,
    NEWEST,
    MOST_REVIEWED
}
