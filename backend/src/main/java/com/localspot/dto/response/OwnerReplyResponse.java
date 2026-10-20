package com.localspot.dto.response;

import java.time.Instant;

/** openapi {@code OwnerReply} — phản hồi của chủ địa điểm dưới review (FR-33). */
public record OwnerReplyResponse(Long id, UserSummary owner, String content, Instant createdAt, Instant updatedAt) {}
