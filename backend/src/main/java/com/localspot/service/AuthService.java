package com.localspot.service;

import com.localspot.dto.request.LoginRequest;
import com.localspot.dto.request.RegisterRequest;
import com.localspot.dto.response.AuthResponse;
import com.localspot.entity.Role;
import com.localspot.entity.User;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.RoleRepository;
import com.localspot.repository.UserRepository;
import com.localspot.security.JwtService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đăng ký, đăng nhập, làm mới phiên, đăng xuất (UC01–UC03, FR-01/03/04). */
@Service
public class AuthService {

    private static final DateTimeFormatter LOCK_TIME =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final ProfileService profiles;
    private final Clock clock;

    /**
     * Hash giả để so khi email không tồn tại: đăng nhập sai email và sai mật khẩu tốn cùng thời gian BCrypt, không cho
     * đo thời gian phản hồi để dò email nào đã đăng ký.
     */
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            RoleRepository roles,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokens,
            ProfileService profiles,
            Clock clock) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.profiles = profiles;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-password");
    }

    /**
     * Tạo tài khoản role USER, chưa xác thực email (được đăng nhập, chưa được viết review — FR-02). Gửi mail xác thực
     * làm ở D4.
     */
    @Transactional
    public void register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim());
        user.getRoles()
                .add(roles.findByName(Role.USER).orElseThrow(() -> new IllegalStateException("Thiếu role USER")));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Hai request đăng ký cùng email chen nhau: UNIQUE(email) ở CSDL là chốt chặn cuối
            throw emailTaken();
        }
    }

    @Transactional
    public AuthResult login(LoginRequest request, ClientInfo client) {
        User user = users.findWithRolesByEmail(normalizeEmail(request.email())).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordMatches) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Email hoặc mật khẩu không đúng.");
        }
        ensureNotLocked(user, clock.instant());
        return authenticated(user, refreshTokens.issueNewFamily(user, client));
    }

    /** Lỗi nghiệp vụ ở đây chỉ là 401 sau khi đã ghi thu hồi — không rollback (xem RefreshTokenService#rotate). */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken, ClientInfo client) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken, client);
        User user = users.findWithRolesById(rotation.userId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, ErrorCode.REFRESH_TOKEN_INVALID, "Tài khoản không còn tồn tại."));
        if (user.isLockedAt(clock.instant())) {
            refreshTokens.revokeAll(user.getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.ACCOUNT_LOCKED, lockMessage(user));
        }
        return authenticated(user, rotation.newRawToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    private AuthResult authenticated(User user, String rawRefreshToken) {
        JwtService.AccessToken access = jwtService.issue(user.getId());
        AuthResponse body = new AuthResponse(access.value(), access.expiresInSeconds(), profiles.toMeResponse(user));
        return new AuthResult(body, rawRefreshToken);
    }

    private static void ensureNotLocked(User user, Instant now) {
        if (user.isLockedAt(now)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_LOCKED, lockMessage(user));
        }
    }

    private static String lockMessage(User user) {
        String message = "Tài khoản bị khóa đến " + LOCK_TIME.format(user.getLockedUntil()) + ".";
        return user.getLockReason() == null ? message : message + " Lý do: " + user.getLockReason();
    }

    /** Email so khớp không phân biệt hoa thường (collation ai_ci); lưu dạng chữ thường cho nhất quán. */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.EMAIL_ALREADY_EXISTS, "Email này đã được đăng ký.");
    }

    /** Body trả cho client + refresh token gốc để controller đặt vào cookie. */
    public record AuthResult(AuthResponse body, String rawRefreshToken) {}
}
