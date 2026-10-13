package com.localspot.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Tiêu chí trao huy hiệu (FR-29, O3 — chốt 2026-10-12). Mã khớp {@code V4__seed_badges.sql}; số liệu từ
 * {@code ContributionQueries#badgeStatsOf} (chỉ nội dung đang hiện). Trao một lần, không thu hồi.
 */
public enum BadgeRule {
    REVIEWER_1("reviews", 1),
    REVIEWER_2("reviews", 10),
    REVIEWER_3("reviews", 25),
    REVIEWER_4("reviews", 50),
    REVIEWER_5("reviews", 100),
    /** 10 địa điểm khác nhau — check-in nhiều lần một quán không tính thêm. */
    EXPLORER("checkin_places", 10),
    PHOTOGRAPHER("photos", 20),
    /** Vote hữu ích hợp lệ ({@code counted}) nhận được. */
    HELPFUL("helpful_votes", 50);

    private final String stat;
    private final int threshold;

    BadgeRule(String stat, int threshold) {
        this.stat = stat;
        this.threshold = threshold;
    }

    /** Mã các huy hiệu đã đạt theo số liệu hiện tại. */
    public static List<String> reached(Map<String, Integer> stats) {
        return Arrays.stream(values())
                .filter(rule -> stats.getOrDefault(rule.stat, 0) >= rule.threshold)
                .map(Enum::name)
                .toList();
    }
}
