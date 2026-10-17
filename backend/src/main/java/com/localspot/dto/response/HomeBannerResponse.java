package com.localspot.dto.response;

import com.localspot.entity.BannerSlot;

/** openapi {@code HomeBanner} — ảnh đang bật của một ô trang chủ; {@code altText} rỗng = ảnh trang trí. */
public record HomeBannerResponse(BannerSlot slot, PhotoResponse image, String altText) {}
