package com.localspot.service;

import com.localspot.config.SecurityProperties;
import com.localspot.entity.RefreshToken;
import com.localspot.entity.User;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.RefreshTokenRepository;
import com.localspot.security.TokenHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh token xoay vòng (S1, U2): mỗi lần refresh, token cũ bị đánh dấu đã dùng và token mới (cùng family) được cấp.
 * Một token đã dùng mà bị gửi lại nghĩa là có hai bên cùng giữ nó — một trong hai là kẻ đánh cắp — nên thu hồi
 * <b>mọi</b> refresh token của người dùng, buộc đăng nhập lại.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository tokens;
    private final SecurityProperties.RefreshToken properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, SecurityProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties.refreshToken();
        this.clock = clock;
    }

    /** Phiên đăng nhập mới → family mới. Trả token gốc (chỉ xuất hiện trong cookie, không lưu). */
    @Transactional
    public String issueNewFamily(User user, ClientInfo client) {
        return issue(user, UUID.randomUUID().toString(), client);
    }

    /**
     * Đổi token cũ lấy token mới. {@code noRollbackFor}: khi phát hiện dùng lại, lệnh thu hồi toàn bộ phải được commit
     * dù sau đó ném lỗi 401 — nếu rollback, kẻ gian vẫn giữ được token hợp lệ.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken, ClientInfo client) {
        RefreshToken current = find(rawToken).orElseThrow(RefreshTokenService::invalid);
        Instant now = clock.instant();
        Long userId = current.getUser().getId();

        if (current.getUsedAt() != null) {
            tokens.revokeAllActiveByUserId(userId, now);
            throw reused();
        }
        if (current.getRevokedAt() != null || !current.getExpiresAt().isAfter(now)) {
            throw invalid();
        }
        // Hai request đồng thời cùng token: chỉ một UPDATE thành công, request thua bị coi là dùng lại
        if (tokens.markUsed(current.getId(), now) == 0) {
            tokens.revokeAllActiveByUserId(userId, now);
            throw reused();
        }
        return new Rotation(userId, issue(current.getUser(), current.getFamilyId(), client));
    }

    /** Đăng xuất: thu hồi token hiện tại nếu còn dùng được. Không lỗi khi token lạ / đã hết hạn (idempotent). */
    @Transactional
    public void revoke(String rawToken) {
        Instant now = clock.instant();
        find(rawToken).filter(t -> t.isActiveAt(now)).ifPresent(t -> t.setRevokedAt(now));
    }

    @Transactional
    public void revokeAll(Long userId) {
        tokens.revokeAllActiveByUserId(userId, clock.instant());
    }

    private String issue(User user, String familyId, ClientInfo client) {
        String raw = TokenHasher.newToken();
        tokens.save(new RefreshToken(
                user,
                TokenHasher.sha256Hex(raw),
                familyId,
                clock.instant().plus(properties.ttl()),
                client.userAgent(),
                client.ipAddress()));
        return raw;
    }

    private Optional<RefreshToken> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(TokenHasher.sha256Hex(rawToken));
    }

    private static ApiException invalid() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.REFRESH_TOKEN_INVALID,
                "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.");
    }

    private static ApiException reused() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.REFRESH_TOKEN_REUSED,
                "Phát hiện phiên đăng nhập bất thường, mọi thiết bị đã được đăng xuất. Vui lòng đăng nhập lại.");
    }

    /** Kết quả xoay vòng: chủ token và token gốc mới. */
    public record Rotation(Long userId, String newRawToken) {}
}
