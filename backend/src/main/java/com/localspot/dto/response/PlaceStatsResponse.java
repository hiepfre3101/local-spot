package com.localspot.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * openapi {@code PlaceStats} (UC26, FR-34). Chuỗi theo ngày trong {@code [from, to]}; {@code ratingDistribution},
 * {@code bayesianScore}, {@code reviewCount} là hiện trạng (không theo khoảng) — cùng số với trang chi tiết.
 */
public record PlaceStatsResponse(
        LocalDate from,
        LocalDate to,
        List<DailyCount> viewsByDay,
        List<DailyCount> checkinsByDay,
        Map<Integer, Long> ratingDistribution,
        BigDecimal bayesianScore,
        int reviewCount) {

    public PlaceStatsResponse {
        viewsByDay = List.copyOf(viewsByDay);
        checkinsByDay = List.copyOf(checkinsByDay);
        ratingDistribution = Collections.unmodifiableMap(new TreeMap<>(ratingDistribution));
    }
}
