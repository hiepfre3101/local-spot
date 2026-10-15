package com.localspot.dto.response;

import com.localspot.entity.ReportReason;
import java.time.Instant;

/** openapi {@code ReportItem} — một báo cáo trong chi tiết nhóm (kiểm duyệt viên đọc mô tả của từng người). */
public record ReportItemResponse(
        Long id, UserSummary reporter, ReportReason reason, String detail, Instant createdAt) {}
