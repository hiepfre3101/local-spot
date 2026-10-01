package com.localspot.security;

import com.localspot.entity.Role;
import com.localspot.entity.User;
import com.localspot.repository.UserRepository;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Access token (đã được Nimbus kiểm chữ ký, issuer, hạn dùng) → {@link UserAuthentication}. Token chỉ mang id người
 * dùng ({@code sub}); quyền nạp từ CSDL mỗi request để khóa tài khoản / gỡ role / xóa tài khoản có hiệu lực ngay (đã
 * chốt 2026-10-01). Đánh đổi: thêm một truy vấn mỗi request — có thể cache Redis sau nếu đo thấy cần.
 */
@Component
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;
    private final Clock clock;

    public UserJwtAuthenticationConverter(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId = parseUserId(jwt.getSubject());
        // Tài khoản đã xóa mềm bị @SQLRestriction loại → token của nó hết dùng được ngay
        User user = userRepository
                .findWithAuthoritiesById(userId)
                .orElseThrow(() -> new InvalidBearerTokenException("Tài khoản không còn tồn tại"));
        if (user.isLockedAt(clock.instant())) {
            throw new LockedException("Tài khoản đang bị khóa");
        }
        return new UserAuthentication(new AuthenticatedUser(user.getId(), user.getEmail()), jwt, authoritiesOf(user));
    }

    private static Set<GrantedAuthority> authoritiesOf(User user) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (Role role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
            role.getPermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getName())));
        }
        return authorities;
    }

    private static Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new InvalidBearerTokenException("Token không hợp lệ", e);
        }
    }
}
