package com.localspot.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * openapi {@code ModerationDecision} — dùng chung cho các endpoint {@code decision} của kiểm duyệt. {@code reason} bắt
 * buộc khi từ chối (kiểm ở service — phụ thuộc {@code decision}). {@code version} (tùy chọn) lệch → 409.
 */
public record ModerationDecisionRequest(
        @NotNull Decision decision, @Size(max = 500) String reason, Integer version) {

    public enum Decision {
        APPROVE,
        REJECT
    }
}
