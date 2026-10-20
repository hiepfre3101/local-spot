package com.localspot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code localspot.storage.*} — kho ảnh tương thích S3: MinIO khi dev, R2 / S3 khi deploy (plan §2 "Hạ tầng").
 *
 * @param endpoint URL S3 API; bỏ trống = AWS S3 theo {@code region}
 * @param pathStyleAccess {@code true} cho MinIO ({@code http://host:9000/bucket/key}); AWS / R2 dùng virtual-host
 * @param publicUrl gốc URL công khai của bucket (chốt 2026-10-06: bucket public-read, URL ổn định) — dev là
 *     {@code http://localhost:9000/localspot}, deploy là domain CDN / R2 custom domain
 * @param createBucket tự tạo bucket + policy public-read khi khởi động — chỉ bật ở dev
 */
@Validated
@ConfigurationProperties("localspot.storage")
public record StorageProperties(
        String endpoint,
        @NotBlank String region,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        boolean pathStyleAccess,
        @NotBlank String publicUrl,
        boolean createBucket) {

    /** URL công khai của một object, không nhân đôi dấu "/". */
    public String publicUrlOf(String key) {
        String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        return base + "/" + key;
    }
}
