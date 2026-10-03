package com.localspot.service;

import com.localspot.entity.User;
import com.localspot.entity.UserTokenType;
import com.localspot.exception.ApiException;
import com.localspot.exception.ErrorCode;
import com.localspot.repository.UserRepository;
import java.time.Clock;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Xác thực email (UC02, FR-02), khôi phục mật khẩu (UC05, FR-05), đổi mật khẩu (FR-06). */
@Service
public class AccountService {

    private final UserRepository users;
    private final UserTokenService userTokens;
    private final AccountMailService mails;
    private final RefreshTokenService refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountService(
            UserRepository users,
            UserTokenService userTokens,
            AccountMailService mails,
            RefreshTokenService refreshTokens,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.users = users;
        this.userTokens = userTokens;
        this.mails = mails;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Cấp link xác thực mới và xếp hàng gửi mail (sau commit). Gọi khi đăng ký và khi yêu cầu gửi lại. */
    @Transactional
    public void sendEmailVerification(User user) {
        mails.sendEmailVerification(user, userTokens.issue(user, UserTokenType.EMAIL_VERIFY));
    }

    /** Bấm link xác thực: gán {@code email_verified_at} → trust nhận {@code base} (requirements §5.1). */
    @Transactional
    public void verifyEmail(String rawToken) {
        User user = userTokens.consume(rawToken, UserTokenType.EMAIL_VERIFY).getUser();
        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(clock.instant());
        }
    }

    /** Đã xác thực thì không gửi gì (204 như thường — openapi chỉ có 204 / 429). */
    @Transactional
    public void resendEmailVerification(Long userId) {
        User user = users.findById(userId).orElseThrow(AccountService::accountGone);
        if (!user.isEmailVerified()) {
            sendEmailVerification(user);
        }
    }

    /**
     * Luôn "thành công" với người gọi dù email không tồn tại — không cho dò email nào đã đăng ký (openapi). Tài khoản
     * đã xóa mềm bị bỏ qua tự động ({@code @SQLRestriction}).
     */
    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .ifPresent(user -> mails.sendPasswordReset(user, userTokens.issue(user, UserTokenType.PASSWORD_RESET)));
    }

    /**
     * Đặt mật khẩu mới bằng link. Thu hồi mọi refresh token: nếu mật khẩu cũ đã lộ, kẻ gian đang giữ phiên cũng bị
     * đăng xuất. Access token đã cấp còn hiệu lực tối đa 15 phút (stateless — đánh đổi đã chấp nhận ở D3).
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User user = userTokens.consume(rawToken, UserTokenType.PASSWORD_RESET).getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAll(user.getId());
    }

    /**
     * Đổi mật khẩu khi đang đăng nhập (FR-06): thu hồi refresh token của <b>các thiết bị khác</b>. Cookie refresh token
     * chỉ gửi tới {@code /api/v1/auth} nên endpoint này không biết token hiện tại là cái nào → thu hồi tất cả rồi cấp
     * family mới cho thiết bị đang thao tác (controller đặt lại cookie). Kết quả đúng yêu cầu: thiết bị này vẫn đăng
     * nhập, mọi thiết bị khác bị đăng xuất.
     *
     * @return refresh token gốc mới cho thiết bị hiện tại
     */
    @Transactional
    public String changePassword(Long userId, String currentPassword, String newPassword, ClientInfo client) {
        User user = users.findById(userId).orElseThrow(AccountService::accountGone);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            // 422 thay vì 401 (chốt 2026-10-03): interceptor frontend hiểu 401 là hết phiên và sẽ refresh / đăng xuất
            throw ApiException.fieldError(
                    ErrorCode.INVALID_CURRENT_PASSWORD, "currentPassword", "Mật khẩu hiện tại không đúng.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAll(user.getId());
        return refreshTokens.issueNewFamily(user, client);
    }

    private static ApiException accountGone() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Tài khoản không còn tồn tại.");
    }
}
