package com.localspot.dto.request;

import com.localspot.entity.ReportReason;
import com.localspot.entity.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * openapi {@code ReportCreateRequest} (UC17, FR-25). {@code detail} bắt buộc với {@code WRONG_INFO} / {@code OTHER}
 * (chốt 2026-10-14 — kiểm duyệt viên không xét được "sai thông tin" nếu không biết sai chỗ nào), kiểm ở service vì phụ
 * thuộc {@code reason}.
 */
public record ReportCreateRequest(
        @NotNull ReportTargetType targetType,
        @NotNull @Positive Long targetId,
        @NotNull ReportReason reason,

        @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự.")
        String detail) {}
