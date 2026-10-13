package com.localspot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code localspot.contribution.*} — điểm đóng góp và huy hiệu (FR-29).
 *
 * @param recalculationCron lịch tính lại toàn bộ + đối soát sự kiện mất (giờ Việt Nam), lệch job rating (03:00)
 */
@Validated
@ConfigurationProperties("localspot.contribution")
public record ContributionProperties(@NotBlank String recalculationCron) {}
