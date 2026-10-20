package com.localspot.controller;

import com.localspot.dto.request.OwnerReplyRequest;
import com.localspot.dto.response.OwnerReplyResponse;
import com.localspot.security.AuthenticatedUser;
import com.localspot.security.OwnershipPermissionEvaluator;
import com.localspot.security.Permissions;
import com.localspot.service.OwnerReplyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/reviews/{reviewId}/reply} — chủ địa điểm phản hồi review (UC25, FR-33). */
@RestController
@RequestMapping("/api/v1/reviews/{reviewId}/reply")
public class OwnerReplyController {

    private final OwnerReplyService replies;

    public OwnerReplyController(OwnerReplyService replies) {
        this.replies = replies;
    }

    @PutMapping
    @PreAuthorize("hasPermission(#reviewId, '" + OwnershipPermissionEvaluator.REVIEW + "', '"
            + Permissions.REVIEW_REPLY_OWN_PLACE + "')")
    public OwnerReplyResponse upsert(
            @AuthenticationPrincipal AuthenticatedUser owner,
            @PathVariable Long reviewId,
            @Valid @RequestBody OwnerReplyRequest request) {
        return replies.upsert(owner.id(), reviewId, request.content());
    }

    @DeleteMapping
    @PreAuthorize("hasPermission(#reviewId, '" + OwnershipPermissionEvaluator.REVIEW + "', '"
            + Permissions.REVIEW_REPLY_OWN_PLACE + "')")
    public ResponseEntity<Void> delete(@PathVariable Long reviewId) {
        replies.delete(reviewId);
        return ResponseEntity.noContent().build();
    }
}
