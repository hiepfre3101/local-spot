package com.localspot.entity;

/**
 * Ô ảnh cố định trên trang chủ (FR-43, UC34) — theo bố cục HomePage, không phải danh sách banner tự do. Thêm ô = migration
 * sửa CHECK + giao diện có chỗ đặt ảnh.
 */
public enum BannerSlot {
    /** Nền khối hero — chỉ đổi ảnh, tiêu đề và ô tìm kiếm giữ nguyên. */
    HERO,
    /** Ảnh trong băng coral mời đăng ký. */
    SIGNUP_BAND,
    /** Ảnh khối "Thành viên làm được gì". */
    COMMUNITY
}
