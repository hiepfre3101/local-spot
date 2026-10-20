package com.localspot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.localspot.config.TrustProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Bảng ví dụ trong requirements §5.1 — mỗi dòng của bảng là một ca kiểm thử. */
class TrustScoreCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    /** Đúng giá trị đã chốt trong application.yml. */
    private final TrustScoreCalculator calculator =
            new TrustScoreCalculator(new TrustProperties(10, 1, 7, 18, 2, 36, 3, 30));

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            Mới tạo, vừa xác thực email                     | true  |   0 |  0 |  0 |  10 | false
            4 tháng, không ai vote hữu ích (sleeper)         | true  | 126 |  0 |  0 |  28 | false
            Mới tạo, 10 vote hữu ích hợp lệ                  | true  |   0 | 10 |  0 |  30 | true
            9 tuần, 6 vote hữu ích hợp lệ                    | true  |  63 |  6 |  0 |  31 | true
            Người dùng tối đa                                | true  | 200 | 50 |  0 | 100 | true
            Người dùng tối đa, 1 vi phạm còn hiệu lực        | true  | 200 | 50 | 25 |  25 | false
            Người dùng tối đa, 2 vi phạm còn hiệu lực        | true  | 200 | 50 | 50 |   0 | false
            Chưa xác thực email: không có base               | false | 200 | 50 |  0 |  90 | true
            Mới tạo, chưa xác thực                           | false |   0 |  0 |  0 |   0 | false
            Tuổi chưa đủ một tuần không được điểm tuổi        | true  |   6 |  0 |  0 |  10 | false
            """)
    void matchesRequirementsExamples(
            String scenario,
            boolean emailVerified,
            int ageDays,
            int helpfulVotes,
            int penaltyPoints,
            int expectedTrust,
            boolean canPublish) {
        int trust = calculator.calculate(new TrustScoreCalculator.Inputs(
                emailVerified, NOW.minus(Duration.ofDays(ageDays)), helpfulVotes, penaltyPoints, NOW));

        assertThat(trust).as(scenario).isEqualTo(expectedTrust);
        assertThat(calculator.canPublishWithoutModeration(trust)).as(scenario).isEqualTo(canPublish);
    }
}
