package com.localspot.security;

/**
 * Các luật giới hạn tần suất (NFR-10). Ngưỡng ở {@code localspot.rate-limit.policies} (chốt 2026-10-04). Giới hạn 5
 * review / 24 giờ (FR-23) <b>không</b> nằm ở đây: đó là một phần cơ chế chống review ảo, đếm trực tiếp trong bảng
 * {@code reviews} để vẫn có hiệu lực khi Redis sập (rate limit ở đây cho qua khi Redis sập).
 */
public enum RateLimitPolicy {
    /** Đăng nhập theo (email, IP) — U2: 5 lần sai / 15 phút; đăng nhập đúng xóa bộ đếm. */
    LOGIN,
    /** Đăng ký theo IP — nới vì nhiều máy có thể chung một IP (wifi trường, phòng bảo vệ). */
    REGISTER,
    /** Quên mật khẩu theo email — chặn spam hộp thư của một người. */
    FORGOT_PASSWORD_EMAIL,
    /** Quên mật khẩu theo IP — chặn dò hàng loạt email từ một nguồn. */
    FORGOT_PASSWORD_IP,
    /** Gửi lại mail xác thực theo tài khoản (UC01 7a). */
    RESEND_VERIFICATION
}
