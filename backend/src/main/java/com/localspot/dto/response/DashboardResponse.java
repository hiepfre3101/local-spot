package com.localspot.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * openapi {@code Dashboard} (UC33, FR-41) — gộp trường của openapi gốc với những gì AdminDashboard vẽ (supervisor chốt
 * 2026-10-17): so với kỳ trước cùng độ dài, khu vực nhiều đánh giá, tuổi của việc tồn cũ nhất.
 *
 * @param totals tổng trong {@code [from, to]}
 * @param previousTotals tổng của kỳ liền trước cùng số ngày — để vẽ ▲▼ so với kỳ trước
 * @param reviewsByCity tối đa 5 thành phố, theo review đang hiện viết trong khoảng
 * @param hotPlaces tối đa 10 địa điểm nhiều review đang hiện mới viết trong khoảng nhất
 * @param pendingCounts việc tồn <b>hiện tại</b> (không theo khoảng ngày), cùng định nghĩa với hàng chờ gộp
 */
public record DashboardResponse(
        LocalDate from,
        LocalDate to,
        Instant generatedAt,
        Totals totals,
        Totals previousTotals,
        List<DailyCount> newUsersByDay,
        List<DailyCount> reviewsByDay,
        List<CityCount> reviewsByCity,
        List<HotPlace> hotPlaces,
        PendingCounts pendingCounts) {

    public DashboardResponse {
        newUsersByDay = List.copyOf(newUsersByDay);
        reviewsByDay = List.copyOf(reviewsByDay);
        reviewsByCity = List.copyOf(reviewsByCity);
        hotPlaces = List.copyOf(hotPlaces);
    }

    /** Tài khoản mới, review được gửi (mọi trạng thái), báo cáo nhận được. */
    public record Totals(long newUsers, long reviews, long reports) {}

    public record CityCount(String city, long reviewCount) {}

    /** {@code newReviewCount}: review đang hiện viết trong khoảng — tiêu chí xếp hạng. */
    public record HotPlace(PlaceSummaryResponse place, long newReviewCount) {}

    /** {@code oldestSubmittedAt}: thời điểm gửi của việc tồn cũ nhất (mọi loại), {@code null} khi hàng chờ trống. */
    public record PendingCounts(long places, long reviews, long reports, long claims, Instant oldestSubmittedAt) {}
}
