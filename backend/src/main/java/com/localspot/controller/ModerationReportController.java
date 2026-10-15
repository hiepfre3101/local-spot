package com.localspot.controller;

import com.localspot.dto.request.ReportDecisionRequest;
import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.ReportGroupDetailResponse;
import com.localspot.dto.response.ReportGroupResponse;
import com.localspot.entity.ReportTargetType;
import com.localspot.security.AuthenticatedUser;
import com.localspot.security.Permissions;
import com.localspot.service.ReportModerationService;
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

/** {@code /api/v1/moderation/reports} — kiểm duyệt viên xử lý báo cáo vi phạm (UC29, FR-37). */
@RestController
@RequestMapping("/api/v1/moderation/reports")
public class ModerationReportController {

    private final ReportModerationService moderation;

    public ModerationReportController(ReportModerationService moderation) {
        this.moderation = moderation;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.REPORT_HANDLE + "')")
    public CursorPage<ReportGroupResponse> queue(
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return moderation.queue(targetType, cursor, limit);
    }

    @GetMapping("/{targetType}/{targetId}")
    @PreAuthorize("hasAuthority('" + Permissions.REPORT_HANDLE + "')")
    public ReportGroupDetailResponse detail(@PathVariable ReportTargetType targetType, @PathVariable Long targetId) {
        return moderation.detail(targetType, targetId);
    }

    @PostMapping("/decision")
    @PreAuthorize("hasAuthority('" + Permissions.REPORT_HANDLE + "')")
    public ResponseEntity<Void> decide(
            @AuthenticationPrincipal AuthenticatedUser moderator, @Valid @RequestBody ReportDecisionRequest request) {
        switch (request.decision()) {
            case HIDE -> moderation.hide(moderator.id(), request);
            case DISMISS -> moderation.dismiss(moderator.id(), request);
        }
        return ResponseEntity.noContent().build();
    }
}
