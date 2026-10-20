package com.localspot.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * openapi {@code ActivityLogEntry} (FR-42). Không trả {@code ip_address}: màn nhật ký cần biết ai làm gì, không cần lộ
 * IP của đồng nghiệp — cột vẫn ở CSDL để tra cứu khi có sự cố.
 */
public record ActivityLogEntryResponse(
        Long id,
        UserSummary actor,
        String action,
        String targetType,
        Long targetId,
        Map<String, Object> metadata,
        Instant createdAt) {}
