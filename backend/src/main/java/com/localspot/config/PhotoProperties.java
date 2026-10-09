package com.localspot.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * {@code localspot.photo.*} — giới hạn upload (NFR-09) và kích thước ảnh sinh ra (FR-16).
 *
 * @param maxPerRequest số ảnh tối đa mỗi request (openapi {@code maxItems: 10})
 * @param maxPerPlace tổng ảnh tối đa của một địa điểm (chốt 2026-10-06)
 * @param maxPerReview tổng ảnh tối đa của một review — bằng số ảnh một lần tải lên (openapi {@code maxItems: 10}), thêm
 *     ảnh sau khi viết cũng không vượt (chốt 2026-10-09)
 * @param maxFileSize dung lượng tối đa mỗi ảnh (NFR-09) — khớp {@code spring.servlet.multipart.max-file-size}
 * @param maxPixels chặn "bom giải nén": ảnh vài trăm KB nhưng hàng trăm megapixel làm tràn bộ nhớ khi giải mã
 * @param jpegQuality chất lượng nén JPEG đầu ra (0–1)
 * @param thumbSize cạnh dài tối đa (px) của bản thumb — thẻ địa điểm
 * @param mediumSize cạnh dài tối đa của bản medium — gallery
 * @param largeSize cạnh dài tối đa của bản large — xem ảnh toàn màn hình
 */
@Validated
@ConfigurationProperties("localspot.photo")
public record PhotoProperties(
        @Positive int maxPerRequest,
        @Positive int maxPerPlace,
        @Positive int maxPerReview,
        @NotNull DataSize maxFileSize,
        @Positive long maxPixels,
        @DecimalMin("0.1") @DecimalMax("1.0") float jpegQuality,
        @Positive int thumbSize,
        @Positive int mediumSize,
        @Positive int largeSize) {}
