package com.localspot.controller;

import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.LeaderboardEntryResponse;
import com.localspot.dto.response.PublicProfileResponse;
import com.localspot.dto.response.UserSummary;
import com.localspot.security.AuthenticatedUser;
import com.localspot.service.CommunityService;
import com.localspot.service.ContributionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Hồ sơ công khai, theo dõi (UC20, FR-28), bảng xếp hạng đóng góp (UC21, FR-29). */
@RestController
@RequestMapping("/api/v1")
public class CommunityController {

    private static final String LIMIT_RANGE = "Số dòng từ 1 đến 100.";

    private final CommunityService communityService;
    private final ContributionService contributionService;

    public CommunityController(CommunityService communityService, ContributionService contributionService) {
        this.communityService = communityService;
        this.contributionService = contributionService;
    }

    @GetMapping("/users/{userId}")
    public PublicProfileResponse profile(@PathVariable Long userId, Authentication authentication) {
        return communityService.profile(userId, Viewers.from(authentication).userId());
    }

    @PutMapping("/users/{userId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long userId) {
        communityService.follow(user.id(), userId);
    }

    @DeleteMapping("/users/{userId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long userId) {
        communityService.unfollow(user.id(), userId);
    }

    @GetMapping("/users/{userId}/followers")
    public CursorPage<UserSummary> followers(
            @PathVariable Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return communityService.followers(userId, cursor, limit);
    }

    @GetMapping("/users/{userId}/following")
    public CursorPage<UserSummary> following(
            @PathVariable Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return communityService.following(userId, cursor, limit);
    }

    /** {@code city} (tùy chọn, khớp {@code places.city}) — sitemap P08 "region" (chốt 2026-10-12). */
    @GetMapping("/leaderboard")
    public List<LeaderboardEntryResponse> leaderboard(
            @RequestParam(defaultValue = "MONTH") ContributionService.Period period,
            @RequestParam(required = false) @Size(max = 100) String city,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = LIMIT_RANGE)
                    @Max(value = 100, message = LIMIT_RANGE)
                    int limit) {
        String normalizedCity = city == null || city.isBlank() ? null : city.strip();
        return contributionService.leaderboard(period, normalizedCity, limit);
    }
}
