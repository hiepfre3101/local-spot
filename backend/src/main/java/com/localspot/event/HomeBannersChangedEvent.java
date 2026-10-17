package com.localspot.event;

import com.localspot.entity.BannerSlot;

/** Ảnh một ô trang chủ đổi (thay ảnh, bật / tắt, sửa mô tả, gỡ) — xóa cache {@code GET /home} sau commit. */
public record HomeBannersChangedEvent(BannerSlot slot) {}
