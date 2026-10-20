package com.localspot.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code localspot.trust.*} — tham số trust score (requirements §5.1). Giá trị đã chốt nằm ở application.yml; tách ra
 * cấu hình để chạy thử nghiệm tỉ lệ bắt review ảo ở Chương 5 mà không sửa code.
 */
@Validated
@ConfigurationProperties("localspot.trust")
public record TrustProperties(
        @PositiveOrZero int base,
        @PositiveOrZero int ageWeight,
        @Positive int ageDaysPerPoint,
        @PositiveOrZero int ageCap,
        @PositiveOrZero int helpfulWeight,
        @PositiveOrZero int helpfulCap,
        @PositiveOrZero int penaltyWeight,
        @Min(0) @Max(100) int publishThreshold) {}
