package com.localspot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Công thức Bayesian (plan §5, FR-22, NFR-20) — các ví dụ số dùng trong báo cáo / khi bảo vệ. */
class RatingCalculatorTest {

    private static final int M = 10;
    private static final double C = 4.0;

    private final RatingCalculator calculator = new RatingCalculator();

    /** Plan §5: quán 5 sao / 1 review không vượt mặt quán 4.6 sao / 300 review. */
    @Test
    void oneFiveStarReviewDoesNotBeatManyGoodOnes() {
        double fewReviews = calculator.bayesian(1, 5.0, M, C);
        double manyReviews = calculator.bayesian(300, 4.6, M, C);

        assertThat(fewReviews).isCloseTo(4.0909, within(1e-4)); // 1/11·5 + 10/11·4
        assertThat(manyReviews).isCloseTo(4.5806, within(1e-4)); // 300/310·4.6 + 10/310·4
        assertThat(manyReviews).isGreaterThan(fewReviews);
        // Trung bình cộng đơn thuần thì ngược lại: 5.0 > 4.6
    }

    @Test
    void scoreMovesFromTheSystemAverageTowardsTheOwnAverageAsReviewsAccumulate() {
        assertThat(calculator.bayesian(10, 5.0, M, C)).isCloseTo(4.5, within(1e-9)); // v = m: đúng nửa đường
        assertThat(calculator.bayesian(1, 1.0, M, C)).isCloseTo(3.7273, within(1e-4)); // một lượt 1 sao không dìm hẳn
        assertThat(calculator.bayesian(1_000_000, 3.0, M, C)).isCloseTo(3.0, within(1e-4)); // nhiều review → ≈ R
        assertThat(calculator.bayesian(5, C, M, C)).isCloseTo(C, within(1e-9)); // R = C → luôn bằng C
    }

    /** Chốt 2026-10-11: chưa có review → 0 ("chưa có đánh giá"), không phải C. */
    @Test
    void noReviewsMeansNoScore() {
        assertThat(calculator.bayesian(0, 0, M, C)).isZero();
        assertThat(calculator.bayesian(0, 5.0, M, C)).isZero();
    }

    @Test
    void zeroThresholdIsThePlainAverage() {
        assertThat(calculator.bayesian(1, 5.0, 0, C)).isEqualTo(5.0);
    }

    @Test
    void scoreStaysWithinTheStarRange() {
        assertThat(calculator.bayesian(3, 5.0, M, 5.0)).isCloseTo(5.0, within(1e-9));
        assertThat(calculator.bayesian(3, 1.0, M, 1.0)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void negativeInputsAreProgrammingErrors() {
        assertThatThrownBy(() -> calculator.bayesian(-1, 4, M, C)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.bayesian(1, 4, -1, C)).isInstanceOf(IllegalArgumentException.class);
    }
}
