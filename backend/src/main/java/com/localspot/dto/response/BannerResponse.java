package com.localspot.dto.response;

import com.localspot.entity.BannerSlot;
import java.time.Instant;

/**
 * openapi {@code Banner} — một ô ảnh trang chủ ở màn quản trị (UC34). Ô chưa có ảnh vẫn có mặt: {@code id}, {@code image},
 * {@code version}, {@code updatedAt}, {@code updatedBy} là {@code null}, trang chủ dùng hình minh hoạ mặc định.
 */
public record BannerResponse(
        BannerSlot slot,
        Long id,
        PhotoResponse image,
        String altText,
        boolean active,
        Integer version,
        Instant updatedAt,
        UserSummary updatedBy) {

    public static BannerResponse empty(BannerSlot slot) {
        return new BannerResponse(slot, null, null, "", false, null, null, null);
    }
}
