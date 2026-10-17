package com.localspot.controller;

import com.localspot.dto.response.CursorPage;
import com.localspot.dto.response.ModerationQueueItemResponse;
import com.localspot.entity.ModerationItemType;
import com.localspot.security.Permissions;
import com.localspot.service.ModerationQueueService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/moderation/queue} — hàng chờ gộp bốn loại việc kiểm duyệt, FIFO (UC27–UC30). */
@RestController
@RequestMapping("/api/v1/moderation/queue")
public class ModerationQueueController {

    private final ModerationQueueService queue;

    public ModerationQueueController(ModerationQueueService queue) {
        this.queue = queue;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.PLACE_APPROVE + "', '" + Permissions.REVIEW_MODERATE + "', '"
            + Permissions.REPORT_HANDLE + "', '" + Permissions.CLAIM_APPROVE + "')")
    public CursorPage<ModerationQueueItemResponse> queue(
            Authentication authentication,
            @RequestParam(required = false) ModerationItemType type,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        var authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return queue.page(ModerationQueueService.visibleTypes(authorities), type, cursor, limit);
    }
}
