package com.localspot.dto.response;

import com.localspot.entity.ClaimStatus;
import java.time.Instant;

/**
 * openapi {@code Claim} — yêu cầu sở hữu trong hàng chờ / màn duyệt (UC30). File minh chứng chỉ có số lượng; xem từng
 * file qua link tạm {@code /moderation/claims/{id}/evidence/{index}}. {@code otherPendingClaimCount}: số yêu cầu đang chờ
 * khác cho cùng địa điểm — sẽ bị tự từ chối nếu duyệt yêu cầu này.
 */
public record ClaimResponse(
        Long id,
        PlaceSummaryResponse place,
        UserSummary claimant,
        String contactPhone,
        String note,
        int evidenceCount,
        ClaimStatus status,
        String rejectReason,
        long otherPendingClaimCount,
        Instant createdAt,
        int version) {}
