package com.localspot.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token ngẫu nhiên cho refresh token / link email. CSDL chỉ lưu SHA-256 (hex, 64 ký tự): lộ bảng không dùng lại được
 * token. Không cần BCrypt như mật khẩu vì token có 256 bit ngẫu nhiên — không đoán / dò từ điển được.
 */
public final class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private TokenHasher() {}

    /** 32 byte ngẫu nhiên, base64url không padding (43 ký tự) — an toàn trong cookie và URL. */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM thiếu SHA-256", e);
        }
    }
}
