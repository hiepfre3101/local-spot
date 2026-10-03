package com.localspot.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Kết quả xác thực một access token: principal là {@link AuthenticatedUser}, authorities gồm {@code ROLE_<tên role>} và
 * tên permission dạng {@code resource:action} (dùng với {@code hasAuthority('place:approve')}).
 */
public class UserAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final AuthenticatedUser principal;
    private final Jwt token;

    public UserAuthentication(
            AuthenticatedUser principal, Jwt token, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return token;
    }
}
