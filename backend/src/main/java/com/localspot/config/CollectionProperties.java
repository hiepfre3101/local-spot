package com.localspot.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code localspot.collection.*} — bộ sưu tập (FR-26, chốt 2026-10-12). Chi tiết bộ sưu tập trả mọi địa điểm trong một
 * mảng (openapi, không phân trang) → trần số địa điểm giữ response có giới hạn.
 *
 * @param maxPerUser số bộ sưu tập tối đa mỗi người (50)
 * @param maxPlaces số địa điểm tối đa mỗi bộ (100)
 */
@Validated
@ConfigurationProperties("localspot.collection")
public record CollectionProperties(
        @Positive int maxPerUser, @Positive int maxPlaces) {}
