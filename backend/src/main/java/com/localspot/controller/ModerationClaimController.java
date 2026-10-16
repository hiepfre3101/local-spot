package com.localspot.controller;

import com.localspot.dto.request.ModerationDecisionRequest;
import com.localspot.dto.response.ClaimResponse;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.EvidenceLinkResponse;
import com.localspot.security.AuthenticatedUser;
import com.localspot.security.Permissions;
import com.localspot.service.ClaimModerationService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
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

/** {@code /api/v1/moderation/claims} — kiểm duyệt viên duyệt yêu cầu sở hữu (UC30, FR-40). */
@RestController
@RequestMapping("/api/v1/moderation/claims")
public class ModerationClaimController {

    private final ClaimModerationService moderation;

    public ModerationClaimController(ClaimModerationService moderation) {
        this.moderation = moderation;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.CLAIM_APPROVE + "')")
    public CursorPage<ClaimResponse> queue(
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return moderation.queue(cursor, limit);
    }

    @GetMapping("/{claimId}")
    @PreAuthorize("hasAuthority('" + Permissions.CLAIM_APPROVE + "')")
    public ClaimResponse detail(@PathVariable Long claimId) {
        return moderation.detail(claimId);
    }

    /**
     * Link tạm 5 phút dạng JSON (không 302 — xem {@link EvidenceLinkResponse}); không cache — link cũ hết hạn, mỗi lần mở
     * phải ký lại.
     */
    @GetMapping("/{claimId}/evidence/{index}")
    @PreAuthorize("hasAuthority('" + Permissions.CLAIM_APPROVE + "')")
    public ResponseEntity<EvidenceLinkResponse> evidence(@PathVariable Long claimId, @PathVariable int index) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.evidenceLink(claimId, index));
    }

    @PostMapping("/{claimId}/decision")
    @PreAuthorize("hasAuthority('" + Permissions.CLAIM_APPROVE + "')")
    public ResponseEntity<Void> decide(
            @AuthenticationPrincipal AuthenticatedUser moderator,
            @PathVariable Long claimId,
            @Valid @RequestBody ModerationDecisionRequest request) {
        switch (request.decision()) {
            case APPROVE -> moderation.approve(moderator.id(), claimId, request.version());
            case REJECT -> moderation.reject(moderator.id(), claimId, request.reason(), request.version());
        }
        return ResponseEntity.noContent().build();
    }
}
