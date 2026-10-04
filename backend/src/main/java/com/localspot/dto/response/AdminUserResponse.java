package com.localspot.dto.response;

import java.time.Instant;
import java.util.List;

/** openapi {@code AdminUser} — một dòng trong màn quản lý người dùng (UC31, P10). */
public record AdminUserResponse(
        Long id,
        String displayName,
        String avatarUrl,
        String email,
        List<String> roles,
        int trustScore,
        Instant lockedUntil,
        Instant createdAt) {

    public AdminUserResponse {
        roles = List.copyOf(roles);
    }
}
