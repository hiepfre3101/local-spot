package com.localspot.controller;

import com.localspot.dto.request.ModerationDecisionRequest;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.ModerationReviewDetailResponse;
import com.localspot.dto.response.ModerationReviewResponse;
import com.localspot.security.AuthenticatedUser;
import com.localspot.security.Permissions;
import com.localspot.service.ReviewModerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/moderation/reviews} — kiểm duyệt viên duyệt review trong hàng chờ (UC28, FR-36). */
@RestController
@RequestMapping("/api/v1/moderation/reviews")
public class ModerationReviewController {

    private final ReviewModerationService moderation;

    public ModerationReviewController(ReviewModerationService moderation) {
        this.moderation = moderation;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.REVIEW_MODERATE + "')")
    public CursorPage<ModerationReviewResponse> queue(
            @AuthenticationPrincipal AuthenticatedUser moderator,
            @RequestParam(required = false) Boolean ipFlagged,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return moderation.queue(ipFlagged, cursor, limit, moderator.id());
    }

    @GetMapping("/{reviewId}")
    @PreAuthorize("hasAuthority('" + Permissions.REVIEW_MODERATE + "')")
    public ModerationReviewDetailResponse detail(
            @AuthenticationPrincipal AuthenticatedUser moderator, @PathVariable Long reviewId) {
        return moderation.detail(reviewId, moderator.id());
    }

    @PostMapping("/{reviewId}/decision")
    @PreAuthorize("hasAuthority('" + Permissions.REVIEW_MODERATE + "')")
    public ResponseEntity<Void> decide(
            @AuthenticationPrincipal AuthenticatedUser moderator,
            @PathVariable Long reviewId,
            @Valid @RequestBody ModerationDecisionRequest request) {
        switch (request.decision()) {
            case APPROVE -> moderation.approve(moderator.id(), reviewId, request.version());
            case REJECT -> moderation.reject(moderator.id(), reviewId, request.reason(), request.version());
        }
        return ResponseEntity.noContent().build();
    }
}
