package com.localspot.controller;

import com.localspot.dto.request.ModerationDecisionRequest;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.ModerationPlaceResponse;
import com.localspot.entity.PlaceStatus;
import com.localspot.security.AuthenticatedUser;
import com.localspot.security.Permissions;
import com.localspot.service.PlaceModerationService;
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

/** {@code /api/v1/moderation/places} — kiểm duyệt viên duyệt địa điểm (UC27, FR-35). */
@RestController
@RequestMapping("/api/v1/moderation/places")
public class ModerationPlaceController {

    private final PlaceModerationService moderation;

    public ModerationPlaceController(PlaceModerationService moderation) {
        this.moderation = moderation;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.PLACE_APPROVE + "')")
    public CursorPage<ModerationPlaceResponse> queue(
            @RequestParam(required = false) PlaceStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return moderation.queue(status, cursor, limit);
    }

    @PostMapping("/{placeId}/decision")
    @PreAuthorize("hasAuthority('" + Permissions.PLACE_APPROVE + "')")
    public ResponseEntity<Void> decide(
            @AuthenticationPrincipal AuthenticatedUser moderator,
            @PathVariable Long placeId,
            @Valid @RequestBody ModerationDecisionRequest request) {
        switch (request.decision()) {
            case APPROVE -> moderation.approve(moderator.id(), placeId, request.version());
            case REJECT -> moderation.reject(moderator.id(), placeId, request.reason(), request.version());
        }
        return ResponseEntity.noContent().build();
    }
}
