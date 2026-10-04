package com.localspot.controller;

import com.localspot.security.AuthenticatedUser;
import com.localspot.security.Permissions;
import com.localspot.service.Viewer;
import org.springframework.security.core.Authentication;

/** Dựng {@link Viewer} từ Spring Security ở tầng controller — service không phụ thuộc SecurityContext. */
final class Viewers {

    private Viewers() {}

    /** Khách (endpoint công khai, không gửi token) → {@link Viewer#ANONYMOUS}. */
    static Viewer from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Viewer.ANONYMOUS;
        }
        boolean moderator = authentication.getAuthorities().stream()
                .anyMatch(a -> Permissions.PLACE_APPROVE.equals(a.getAuthority()));
        return new Viewer(user.id(), moderator);
    }
}
