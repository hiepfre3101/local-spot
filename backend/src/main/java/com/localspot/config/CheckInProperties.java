package com.localspot.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code localspot.checkin.*} — check-in (FR-24, U5). Tách ra cấu hình để chỉnh khi thử thực địa mà không sửa code.
 *
 * @param radiusM khoảng cách tối đa từ vị trí người dùng tới địa điểm (200 m)
 * @param maxAccuracyM độ chính xác GPS tệ nhất còn chấp nhận (100 m) — kém hơn thường là vị trí ước lượng theo IP / wifi
 */
@Validated
@ConfigurationProperties("localspot.checkin")
public record CheckInProperties(
        @Positive int radiusM, @Positive int maxAccuracyM) {}
