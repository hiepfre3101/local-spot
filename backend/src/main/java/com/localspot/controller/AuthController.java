package com.localspot.controller;

import com.localspot.dto.request.LoginRequest;
import com.localspot.dto.request.RegisterRequest;
import com.localspot.dto.response.AuthResponse;
import com.localspot.security.RefreshTokenCookies;
import com.localspot.service.AuthService;
import com.localspot.service.ClientInfo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/auth} — openapi tag Auth. Refresh token chỉ đi qua cookie HttpOnly, không bao giờ ở body (S1). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookies cookies;

    public AuthController(AuthService authService, RefreshTokenCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withRefreshCookie(authService.login(request, clientInfo(http)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest http) {
        return withRefreshCookie(authService.refresh(refreshCookie(http), clientInfo(http)));
    }

    /** Luôn xóa cookie và trả 204, kể cả khi token đã hết hạn — đăng xuất không bao giờ "thất bại" với người dùng. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        authService.logout(refreshCookie(http));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthService.AuthResult result) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.issue(result.rawRefreshToken()).toString())
                .body(result.body());
    }

    private String refreshCookie(HttpServletRequest http) {
        Cookie[] all = http.getCookies();
        if (all == null) {
            return null;
        }
        return Arrays.stream(all)
                .filter(c -> cookies.cookieName().equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /** IP lấy từ {@code getRemoteAddr()}: sau nginx, {@code forward-headers-strategy} đã thay bằng IP thật. */
    private static ClientInfo clientInfo(HttpServletRequest http) {
        return new ClientInfo(http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
    }
}
