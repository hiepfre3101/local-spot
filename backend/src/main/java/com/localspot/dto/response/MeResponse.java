package com.localspot.dto.response;

import java.util.List;

/**
 * openapi {@code MeResponse} — hồ sơ của chính người đang đăng nhập. {@code trustScore} chỉ trả cho chính chủ (giải
 * thích vì sao review vào hàng chờ).
 */
public record MeResponse(
        Long id,
        String displayName,
        String avatarUrl,
        String email,
        String bio,
        boolean emailVerified,
        List<String> roles,
        List<String> permissions,
        int trustScore,
        List<Long> ownedPlaceIds) {

    public MeResponse {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
        ownedPlaceIds = List.copyOf(ownedPlaceIds);
    }
}
