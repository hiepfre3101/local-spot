package com.localspot.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code localspot.violation.*} — một vi phạm (requirements §5.1, chốt Q3): +{@code points} vào penalty_score, hết hiệu
 * lực sau {@code ttl}. Mức phạt được chụp vào {@code user_violations.points} lúc ghi nên đổi cấu hình không làm sai lịch
 * sử.
 *
 * @param points điểm phạt mỗi vi phạm (25 → trust −75 với {@code penalty-weight} 3)
 * @param ttl thời gian hiệu lực (30 ngày)
 */
@Validated
@ConfigurationProperties("localspot.violation")
public record ViolationProperties(
        @Positive int points, @NotNull Duration ttl) {}
