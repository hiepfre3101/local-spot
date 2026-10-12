package com.localspot.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code localspot.rating.*} — tham số xếp hạng Bayesian (FR-22). Tách ra cấu hình để thử các giá trị {@code m} khác nhau
 * khi viết Chương 5 mà không sửa code.
 *
 * @param minReviews {@code m} — số review để điểm của địa điểm "đáng tin" ngang trung bình toàn hệ thống (đã chốt: 10)
 * @param recalculationCron lịch tính lại toàn bộ (D4, mặc định 03:00 giờ Việt Nam)
 */
@Validated
@ConfigurationProperties("localspot.rating")
public record RatingProperties(
        @PositiveOrZero int minReviews, @NotBlank String recalculationCron) {}
