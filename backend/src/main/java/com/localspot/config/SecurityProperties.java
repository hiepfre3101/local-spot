package com.localspot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code localspot.security.*} — thời hạn token theo NFR-06, cookie refresh token theo S1. */
@Validated
@ConfigurationProperties("localspot.security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt, @Valid @NotNull RefreshToken refreshToken) {

    /**
     * @param secret base64 của khóa HS256, tối thiểu 32 byte (kiểm tra khi tạo khóa); không có mặc định ở prod
     */
    public record Jwt(
            @NotBlank String issuer,
            @NotBlank String secret,
            @NotNull Duration accessTokenTtl) {}

    public record RefreshToken(
            @NotNull Duration ttl,
            @NotBlank String cookieName,
            @NotBlank String cookiePath,
            boolean cookieSecure) {}
}
