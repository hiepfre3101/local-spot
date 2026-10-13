package com.localspot.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * openapi {@code PublicProfile} — không có email (NFR-11). {@code reviewCount} chỉ đếm review đang hiện;
 * {@code followedByMe} luôn {@code false} với khách.
 */
public record PublicProfileResponse(
        Long id,
        String displayName,
        String avatarUrl,
        String bio,
        Instant joinedAt,
        int reviewCount,
        long followerCount,
        long followingCount,
        int contributionPoints,
        List<BadgeResponse> badges,
        boolean followedByMe) {

    public PublicProfileResponse {
        badges = List.copyOf(badges);
    }
}
