package com.localspot.security;

import com.localspot.config.SecurityProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie refresh token theo S1: HttpOnly (JavaScript không đọc được — chống XSS lấy token), Secure, SameSite=Strict,
 * Path=/api/v1/auth (chỉ gửi tới endpoint auth, không đi kèm mọi request API).
 */
@Component
public class RefreshTokenCookies {

    private final SecurityProperties.RefreshToken properties;

    public RefreshTokenCookies(SecurityProperties properties) {
        this.properties = properties.refreshToken();
    }

    public String cookieName() {
        return properties.cookieName();
    }

    public ResponseCookie issue(String rawToken) {
        return base(rawToken).maxAge(properties.ttl()).build();
    }

    /** Ghi đè bằng cookie rỗng, hết hạn ngay — trình duyệt xóa cookie. */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(properties.cookiePath());
    }
}
