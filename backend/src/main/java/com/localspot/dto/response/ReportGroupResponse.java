package com.localspot.dto.response;

import com.localspot.entity.ReportReason;
import com.localspot.entity.ReportTargetType;
import java.time.Instant;
import java.util.Map;

/**
 * openapi {@code ReportGroup} — một dòng hàng đợi UC29: mọi báo cáo đang mở về cùng một đối tượng.
 *
 * @param targetPreview tên địa điểm / đoạn trích review, bình luận / tên hiển thị; {@code null} khi đối tượng đã bị xóa
 * @param targetStatus trạng thái hiện tại của đối tượng ({@code APPROVED}, {@code PUBLISHED}, {@code VISIBLE},
 *     {@code ACTIVE} / {@code LOCKED} với người dùng…) hoặc {@code DELETED} — xử lý khi đã xóa chỉ đóng báo cáo (UC29 3c)
 * @param reasons số báo cáo theo lý do
 */
public record ReportGroupResponse(
        ReportTargetType targetType,
        Long targetId,
        String targetPreview,
        String targetStatus,
        int reportCount,
        Map<ReportReason, Integer> reasons,
        Instant firstReportedAt) {}
