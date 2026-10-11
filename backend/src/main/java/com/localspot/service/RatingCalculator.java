package com.localspot.service;

import org.springframework.stereotype.Component;

/**
 * Công thức xếp hạng Bayesian (plan §5, FR-22) — hàm thuần, không đụng CSDL, để kiểm thử đơn vị (NFR-20) và trình bày
 * trong báo cáo:
 *
 * <pre>
 * điểm = (v / (v + m)) × R + (m / (v + m)) × C
 *   v = số review PUBLISHED của địa điểm      R = trung bình sao của địa điểm
 *   m = ngưỡng review (cấu hình, đã chốt 10)  C = trung bình sao toàn hệ thống
 * </pre>
 *
 * Ý nghĩa: coi như mỗi địa điểm có sẵn {@code m} review "ảo" bằng đúng mức trung bình C; review thật càng nhiều thì điểm
 * càng nghiêng về R. Ví dụ với C = 4.0, m = 10: quán 5 sao / 1 review được 4.091, còn quán 4.6 sao / 300 review được
 * 4.581 — quán ít review không vượt mặt quán nhiều review chỉ nhờ một lượt 5 sao.
 *
 * <p><b>v = 0 → 0</b> (chốt 2026-10-11), lệch khỏi công thức thuần (vốn cho đúng C): chưa có bằng chứng thì chưa có
 * điểm — giao diện hiện "Chưa có đánh giá" và địa điểm đứng sau mọi địa điểm đã có review khi sắp theo điểm, thay vì
 * đứng ngang mức trung bình (≈ 4) trên những quán thật sự có nhiều review 3.9 sao.
 */
@Component
public class RatingCalculator {

    /**
     * @param reviewCount v ≥ 0
     * @param average R — trung bình sao (1–5) của địa điểm; bỏ qua khi v = 0
     * @param minReviews m ≥ 0 (m = 0: điểm = R)
     * @param systemAverage C — trung bình sao toàn hệ thống (1–5); có ít nhất một review khi v &gt; 0 nên luôn xác định
     * @return điểm trong [0, 5], chưa làm tròn
     */
    public double bayesian(long reviewCount, double average, int minReviews, double systemAverage) {
        if (reviewCount < 0 || minReviews < 0) {
            throw new IllegalArgumentException("v và m không được âm: v=" + reviewCount + ", m=" + minReviews);
        }
        if (reviewCount == 0) {
            return 0;
        }
        double v = reviewCount;
        double m = minReviews;
        return v / (v + m) * average + m / (v + m) * systemAverage;
    }
}
