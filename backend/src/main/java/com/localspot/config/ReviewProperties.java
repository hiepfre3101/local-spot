package com.localspot.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code localspot.review.*} — chống review ảo khi viết review (FR-23, requirements §5). Ngưỡng trust để đăng ngay nằm ở
 * {@link TrustProperties#publishThreshold()}.
 *
 * @param maxPerWindow số review tối đa một tài khoản viết trong {@code window} (N = 5 — đếm trong CSDL, không qua Redis)
 * @param window cửa sổ trượt của giới hạn trên (24 giờ)
 * @param ipAlertCount địa điểm đã nhận chừng này review từ cùng dải IP trong {@code ipWindow} thì review kế tiếp từ dải
 *     đó vào hàng chờ + gắn cờ (U3)
 * @param ipWindow cửa sổ của luật cảnh báo IP (24 giờ)
 */
@Validated
@ConfigurationProperties("localspot.review")
public record ReviewProperties(
        @Positive int maxPerWindow,
        @NotNull Duration window,
        @Positive int ipAlertCount,
        @NotNull Duration ipWindow) {}
