package com.localspot.service;

import com.localspot.config.SecurityProperties;
import com.localspot.entity.User;
import com.localspot.entity.UserToken;
import com.localspot.entity.UserTokenType;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.UserTokenRepository;
import com.localspot.security.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Token một lần trong link email (U1): xác thực email 24 h, đặt lại mật khẩu 30 phút. CSDL chỉ lưu SHA-256. */
@Service
public class UserTokenService {

    private final UserTokenRepository tokens;
    private final SecurityProperties properties;
    private final Clock clock;

    public UserTokenService(UserTokenRepository tokens, SecurityProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    /** Cấp token mới, vô hiệu token cũ chưa dùng cùng loại. Trả token gốc để đưa vào link (không lưu). */
    @Transactional
    public String issue(User user, UserTokenType type) {
        Instant now = clock.instant();
        tokens.invalidateUnused(user.getId(), type, now);
        String raw = TokenHasher.newToken();
        tokens.save(new UserToken(user, type, TokenHasher.sha256Hex(raw), now.plus(ttlOf(type))));
        return raw;
    }

    /**
     * Dùng token: hợp lệ thì đánh dấu đã dùng và trả token (kèm người dùng). Không tồn tại, hết hạn hay đã dùng đều trả
     * cùng một lỗi 410 — không cho dò token nào từng tồn tại.
     */
    @Transactional
    public UserToken consume(String rawToken, UserTokenType type) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalid();
        }
        Instant now = clock.instant();
        UserToken token = tokens.findByTokenHashAndType(TokenHasher.sha256Hex(rawToken), type)
                .filter(t -> t.isUsableAt(now))
                .orElseThrow(UserTokenService::invalid);
        if (tokens.markUsed(token.getId(), now) == 0) {
            throw invalid();
        }
        return token;
    }

    private Duration ttlOf(UserTokenType type) {
        return switch (type) {
            case EMAIL_VERIFY -> properties.emailVerificationTtl();
            case PASSWORD_RESET -> properties.passwordResetTtl();
        };
    }

    private static ApiException invalid() {
        return new ApiException(
                HttpStatus.GONE,
                ErrorCode.TOKEN_INVALID,
                "Liên kết đã hết hạn hoặc đã được sử dụng. Vui lòng yêu cầu liên kết mới.");
    }
}
