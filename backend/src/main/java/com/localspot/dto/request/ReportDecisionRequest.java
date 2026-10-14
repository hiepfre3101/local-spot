package com.localspot.dto.request;

import com.localspot.entity.ReportReason;
import com.localspot.entity.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * openapi {@code ReportDecision} (UC29) — xử lý <b>mọi</b> báo cáo đang mở về một đối tượng.
 *
 * <ul>
 *   <li>{@code HIDE}: xác nhận vi phạm — ẩn đối tượng (người dùng: không có gì để ẩn, chỉ ghi vi phạm — chốt 2026-10-14)
 *       và ghi vi phạm theo luật O9; báo cáo → RESOLVED.
 *   <li>{@code DISMISS}: không vi phạm — báo cáo → DISMISSED, không ảnh hưởng ai.
 * </ul>
 *
 * {@code confirmedReason} bắt buộc khi ẩn địa điểm (O9: lý do quyết định người đề xuất có bị ghi vi phạm không) và phải là
 * một lý do có trong các báo cáo đang mở; với loại khác thì tùy chọn, chỉ để ghi nhật ký.
 */
public record ReportDecisionRequest(
        @NotNull ReportTargetType targetType,
        @NotNull @Positive Long targetId,
        @NotNull Decision decision,
        ReportReason confirmedReason,
        @Size(max = 500) String note) {

    public enum Decision {
        HIDE,
        DISMISS
    }
}
