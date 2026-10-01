package com.localspot.controller;

import com.localspot.dto.response.MeResponse;
import com.localspot.security.AuthenticatedUser;
import com.localspot.service.ProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/me} — openapi tag Me. D3 chỉ có GET (frontend nạp lại hồ sơ sau khi refresh trang); sửa hồ sơ, đổi
 * mật khẩu, xóa tài khoản làm ở các mục sau.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final ProfileService profileService;

    public MeController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return profileService.me(user.id());
    }
}
